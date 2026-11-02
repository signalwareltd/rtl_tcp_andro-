package com.sdrtouch.rtlsdr.driver;
import android.hardware.usb.*;import android.content.*;import android.app.PendingIntent;
import com.sdrtouch.testing.FakeContext;import com.sdrtouch.tools.*;import com.sdrtouch.core.SdrTcpArguments;import com.sdrtouch.core.devices.SdrDevice;
import org.junit.Test;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import java.util.function.BooleanSupplier;import static org.junit.Assert.*;

public class RtlSdrDeviceOfflineTest {
 static final SdrTcpArguments ARGS=SdrTcpArguments.fromString("-a 127.0.0.1 -p 14423 -f 91800000 -s 250000 -g 0 -P 0 -T 0");
 static void await(CountDownLatch l){try{assertTrue(l.await(1,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}}
 static void until(BooleanSupplier s)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(!s.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(1);assertTrue(s.getAsBoolean());}
 static class FutureProbe extends AsyncFuture<UsbDeviceConnection>{final CountDownLatch getting=new CountDownLatch(1);@Override public UsbDeviceConnection get(long t,TimeUnit u)throws InterruptedException,ExecutionException,TimeoutException{getting.countDown();return super.get(t,u);}}
 static class Backend implements RtlSdrDevice.NativeSession {
  final AtomicInteger initialized=new AtomicInteger(),opened=new AtomicInteger(),closed=new AtomicInteger(),disposed=new AtomicInteger();Runnable openHook,closeHook;boolean openResult=true;RuntimeException openError;
  public long initialize(){initialized.incrementAndGet();return 123;}
  public boolean open(long h,int fd,SdrTcpArguments a,String path){assertEquals(123,h);assertEquals(42,fd);opened.incrementAndGet();if(openHook!=null)openHook.run();if(openError!=null)throw openError;return openResult;}
  public void close(long h){closed.incrementAndGet();if(closeHook!=null)closeHook.run();}
  public void dispose(long h){disposed.incrementAndGet();}
 }
 static class Closed implements SdrDevice.OnStatusListener {
  final CountDownLatch done=new CountDownLatch(1);final AtomicInteger count=new AtomicInteger();volatile Throwable error;volatile int connectionsClosedAtNotification,disposedAtNotification;final Backend backend;final UsbDeviceConnection connection;
  Closed(Backend b,UsbDeviceConnection c){backend=b;connection=c;}
  public void onOpen(SdrDevice d){}
  public void onClosed(Throwable e){error=e;connectionsClosedAtNotification=connection.closes.get();disposedAtNotification=backend.disposed.get();count.incrementAndGet();done.countDown();}
 }
 static RtlSdrDevice device(FakeContext c,Backend b,RtlSdrDevice.PermissionSource source,long timeout,Closed listener){RtlSdrDevice d=new RtlSdrDevice(c,c.device,b,source,timeout);d.addOnStatusListener(listener);return d;}
 @Test(timeout=3000) public void closeWakesPendingWorkerWithoutNativeEntry()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();FutureProbe f=new FutureProbe();Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(f.getting);d.close();await(l.done);assertTrue(f.isCancelled());assertEquals(0,b.opened.get());assertEquals(0,b.closed.get());assertEquals(1,b.disposed.get());assertEquals(1,l.count.get());assertNull(l.error);
 }
 @Test(timeout=3000) public void closeBeforeFuturePublicationClosesCompletedLateResult()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();CountDownLatch factory=new CountDownLatch(1),release=new CountDownLatch(1);Closed l=new Closed(b,c.manager.connection);
  RtlSdrDevice d=device(c,b,(x,y)->{factory.countDown();await(release);return f;},20000,l);d.openAsync(ARGS);await(factory);d.close();f.setDone(c.manager.connection);release.countDown();await(l.done);assertEquals(0,b.opened.get());assertEquals(1,c.manager.connection.closes.get());assertEquals(1,b.disposed.get());assertNull(l.error);
 }
 @Test(timeout=3000) public void timeoutCancelsPendingFutureAndDisposes()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();FutureProbe f=new FutureProbe();Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20,l);d.openAsync(ARGS);await(l.done);assertTrue(f.isCancelled());assertTrue(l.error instanceof TimeoutException);assertEquals(0,b.opened.get());assertEquals(1,b.disposed.get());
 }
 @Test(timeout=3000) public void realPermissionOwnerCancelledByWorkerClose()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,UsbPermissionObtainer::obtainFdFor,20000,l);d.openAsync(ARGS);until(()->c.manager.pending!=null);PendingIntent pi=c.manager.pending;BroadcastReceiver r=c.permissionReceiver;d.close();await(l.done);c.manager.permission=true;c.deliverQueued(pi,r);assertEquals(0,c.manager.opens.get());assertEquals(0,b.opened.get());assertEquals(0,c.registrations.size());assertEquals(1,b.disposed.get());assertEquals(1,l.count.get());
 }
 @Test(timeout=3000) public void closeDuringFdReadPreventsNativeClaim()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();f.setDone(c.manager.connection);CountDownLatch fd=new CountDownLatch(1),release=new CountDownLatch(1);c.manager.connection.fdHook=()->{fd.countDown();await(release);};Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(fd);d.close();release.countDown();await(l.done);assertEquals(0,b.opened.get());assertEquals(1,c.manager.connection.closes.get());assertEquals(1,b.disposed.get());
 }
 @Test(timeout=3000) public void activeCloseRequestsBackendOnceAndClosesBeforeNotification()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();f.setDone(c.manager.connection);CountDownLatch opened=new CountDownLatch(1),stop=new CountDownLatch(1);b.openHook=()->{opened.countDown();await(stop);};b.closeHook=stop::countDown;Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(opened);d.close();d.close();await(l.done);assertEquals(1,b.opened.get());assertEquals(1,b.closed.get());assertEquals(1,l.connectionsClosedAtNotification);assertEquals(1,l.disposedAtNotification);assertNull(l.error);
 }
 @Test(timeout=3000) public void duplicateOpenCannotCompleteOriginalOperationEarly()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();FutureProbe f=new FutureProbe();Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(f.getting);d.openAsync(ARGS);assertEquals(0,l.count.get());d.close();await(l.done);assertEquals(1,l.count.get());assertEquals(0,b.opened.get());
 }
 @Test(timeout=2000) public void closeBeforeOpenDisposesExactlyOnce()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AtomicInteger requests=new AtomicInteger();Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->{requests.incrementAndGet();return new AsyncFuture<>();},20000,l);d.close();d.close();d.openAsync(ARGS);assertEquals(1,b.disposed.get());assertEquals(0,requests.get());assertEquals(0,l.count.get());
 }
 @Test(timeout=3000) public void completedFutureBeforeGetStartsFakeBackendImmediately()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();f.setDone(c.manager.connection);Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(l.done);assertEquals(1,b.opened.get());assertEquals(1,c.manager.connection.closes.get());assertEquals(1,b.disposed.get());assertNull(l.error);
 }
 @Test(timeout=3000) public void deniedConnectionNeverEntersNative()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();f.setDone(null);Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(l.done);assertTrue(l.error instanceof IllegalStateException);assertEquals(0,b.opened.get());assertEquals(1,b.disposed.get());
 }
 @Test(timeout=3000) public void frameworkFailureIsUnwrappedAndDisposed()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();RuntimeException cause=new SecurityException("permission");f.trySetException(cause);Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(l.done);assertSame(cause,l.error);assertEquals(0,b.opened.get());assertEquals(1,b.disposed.get());
 }
 @Test(timeout=3000) public void fdCloseFailureDoesNotSkipDisposeOrNotification()throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<>();f.setDone(c.manager.connection);RuntimeException error=new IllegalStateException("close");c.manager.connection.closeError=error;Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20000,l);d.openAsync(ARGS);await(l.done);assertSame(error,l.error);assertEquals(1,b.disposed.get());assertEquals(1,l.count.get());
 }

 @Test(timeout=3000) public void completionWinningTimeoutCleanupStillClosesResult()throws Exception{
  assertFailedWaitReclaimsCompletedResult(false);
 }
 @Test(timeout=3000) public void completionWinningInterruptedCleanupStillClosesResult()throws Exception{
  assertFailedWaitReclaimsCompletedResult(true);
 }
 private void assertFailedWaitReclaimsCompletedResult(boolean interrupted)throws Exception{
  FakeContext c=new FakeContext();Backend b=new Backend();
  AsyncFuture<UsbDeviceConnection> f=new AsyncFuture<UsbDeviceConnection>(){
   boolean first=true;
   @Override public UsbDeviceConnection get(long t,TimeUnit u)throws InterruptedException,ExecutionException,TimeoutException{
    if(first){first=false;setDone(c.manager.connection);if(interrupted)throw new InterruptedException();throw new TimeoutException();}
    return super.get(t,u);
   }
  };
  Closed l=new Closed(b,c.manager.connection);RtlSdrDevice d=device(c,b,(x,y)->f,20,l);
  d.openAsync(ARGS);await(l.done);
  assertTrue(interrupted?l.error instanceof InterruptedException:l.error instanceof TimeoutException);
  assertEquals(0,b.opened.get());assertEquals(1,c.manager.connection.closes.get());
  assertEquals(1,l.connectionsClosedAtNotification);assertEquals(1,b.disposed.get());
 }
}
