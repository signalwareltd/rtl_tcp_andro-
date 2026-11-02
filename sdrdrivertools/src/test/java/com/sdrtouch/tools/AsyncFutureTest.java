package com.sdrtouch.tools;

import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class AsyncFutureTest {
    @Test(timeout=2000) public void completionBeforeGetDoesNotWait() throws Exception {
        AsyncFuture<String> f=new AsyncFuture<>();f.setDone("ready");assertEquals("ready",f.get());assertEquals("ready",f.get(0,TimeUnit.NANOSECONDS));
    }
    @Test(timeout=2000) public void nullIsACompletedResult() throws Exception {AsyncFuture<Object> f=new AsyncFuture<>();f.setDone(null);assertNull(f.get());assertTrue(f.isDone());}
    @Test(timeout=2000) public void cancellationWakesAllWaiters() throws Exception {
        AsyncFuture<Object> f=new AsyncFuture<>();CountDownLatch began=new CountDownLatch(4),ended=new CountDownLatch(4);AtomicInteger cancelled=new AtomicInteger();
        for(int i=0;i<4;i++){Thread t=new Thread(()->{began.countDown();try{f.get();}catch(CancellationException e){cancelled.incrementAndGet();}catch(Exception e){throw new AssertionError(e);}finally{ended.countDown();}});t.setDaemon(true);t.start();}
        assertTrue(began.await(1,TimeUnit.SECONDS));assertTrue(f.cancel(true));assertTrue(ended.await(1,TimeUnit.SECONDS));assertEquals(4,cancelled.get());assertTrue(f.isCancelled());assertTrue(f.isDone());
    }
    @Test(timeout=2000) public void completionWakesAllWaiters() throws Exception {
        AsyncFuture<String> f=new AsyncFuture<>();CountDownLatch ended=new CountDownLatch(4);AtomicInteger ok=new AtomicInteger();
        for(int i=0;i<4;i++){Thread t=new Thread(()->{try{if("value".equals(f.get()))ok.incrementAndGet();}catch(Exception e){throw new AssertionError(e);}finally{ended.countDown();}});t.setDaemon(true);t.start();}
        f.setDone("value");assertTrue(ended.await(1,TimeUnit.SECONDS));assertEquals(4,ok.get());
    }
    @Test(timeout=2000) public void cancellationBeforeGetIsTerminal() throws Exception {
        AsyncFuture<Object> f=new AsyncFuture<>();assertTrue(f.cancel(false));assertFalse(f.trySetDone(new Object()));assertFalse(f.cancel(true));
        try{f.get();fail();}catch(CancellationException expected){}
    }
    @Test(timeout=2000) public void completionCannotBeReplacedOrCancelled() throws Exception {
        AsyncFuture<String> f=new AsyncFuture<>();assertTrue(f.trySetDone("first"));assertFalse(f.trySetDone("late"));assertFalse(f.cancel(true));assertEquals("first",f.get());assertFalse(f.isCancelled());
    }
    @Test(timeout=2000) public void failureIsTerminalAndVisible() throws Exception {
        AsyncFuture<Object> f=new AsyncFuture<>();IllegalStateException cause=new IllegalStateException("framework");assertTrue(f.trySetException(cause));assertFalse(f.trySetDone(new Object()));
        try{f.get();fail();}catch(ExecutionException e){assertSame(cause,e.getCause());}
    }
    @Test(timeout=2000) public void zeroAndNegativeTimeoutsDoNotWait() throws Exception {
        for(long n:new long[]{0,-1})try{new AsyncFuture<>().get(n,TimeUnit.NANOSECONDS);fail();}catch(TimeoutException expected){}
    }
    @Test(timeout=2000) public void subMillisecondTimeoutDoesNotBecomeInfiniteWait() throws Exception {
        try{new AsyncFuture<>().get(1,TimeUnit.NANOSECONDS);fail();}catch(TimeoutException expected){}
    }
    @Test(timeout=2000) public void timedGetThrowsRatherThanReturningNull() throws Exception {
        AsyncFuture<Object> f=new AsyncFuture<>();try{f.get(5,TimeUnit.MILLISECONDS);fail();}catch(TimeoutException expected){}assertFalse(f.isDone());assertFalse(f.isCancelled());
    }
    @Test(timeout=2000) public void interruptionDoesNotCompleteTheFuture() throws Exception {
        AsyncFuture<Object> f=new AsyncFuture<>();CountDownLatch done=new CountDownLatch(1);AtomicBoolean interrupted=new AtomicBoolean();
        Thread t=new Thread(()->{try{f.get();}catch(InterruptedException e){interrupted.set(true);}catch(Exception e){throw new AssertionError(e);}finally{done.countDown();}});t.setDaemon(true);t.start();t.interrupt();assertTrue(done.await(1,TimeUnit.SECONDS));assertTrue(interrupted.get());assertFalse(f.isDone());f.cancel(false);
    }
    @Test(timeout=3000) public void spuriousNotificationDoesNotReturnBeforeCompletion() throws Exception {
        AsyncFuture<String> f=new AsyncFuture<>();Field field=AsyncFuture.class.getDeclaredField("locker");field.setAccessible(true);Object lock=field.get(f);AtomicReference<String> value=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);
        Thread t=new Thread(()->{try{value.set(f.get());}catch(Exception e){throw new AssertionError(e);}finally{done.countDown();}});t.setDaemon(true);t.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);while(t.getState()!=Thread.State.WAITING&&System.nanoTime()<deadline)Thread.yield();assertEquals(Thread.State.WAITING,t.getState());
        synchronized(lock){lock.notifyAll();}assertFalse(done.await(20,TimeUnit.MILLISECONDS));assertNull(value.get());f.setDone("after");assertTrue(done.await(1,TimeUnit.SECONDS));assertEquals("after",value.get());
    }
    @Test(timeout=5000) public void cancellationAndCompletionHaveExactlyOneWinner() throws Exception {
        for(int i=0;i<100;i++){AsyncFuture<String> f=new AsyncFuture<>();CountDownLatch go=new CountDownLatch(1);AtomicBoolean completion=new AtomicBoolean(),cancel=new AtomicBoolean();
            Thread a=new Thread(()->{try{go.await();completion.set(f.trySetDone("value"));}catch(Exception e){throw new AssertionError(e);}});Thread b=new Thread(()->{try{go.await();cancel.set(f.cancel(true));}catch(Exception e){throw new AssertionError(e);}});a.setDaemon(true);b.setDaemon(true);a.start();b.start();go.countDown();a.join(1000);b.join(1000);assertFalse(a.isAlive());assertFalse(b.isAlive());assertTrue(completion.get()^cancel.get());
            if(cancel.get()){try{f.get();fail();}catch(CancellationException expected){}}else assertEquals("value",f.get());
        }
    }
    @Test(timeout=2000) public void nullTimeUnitIsRejectedEvenForCompletedFuture() throws Exception {AsyncFuture<String> f=new AsyncFuture<>();f.setDone("x");try{f.get(1,null);fail();}catch(NullPointerException expected){}}
}
