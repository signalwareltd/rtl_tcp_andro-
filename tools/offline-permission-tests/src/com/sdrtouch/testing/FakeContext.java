package com.sdrtouch.testing;
import android.content.*;import android.hardware.usb.*;import android.app.PendingIntent;import java.util.*;import java.util.concurrent.CopyOnWriteArrayList;
public class FakeContext extends Context {
 public static class Registration {public final BroadcastReceiver receiver;public final IntentFilter filter;public final int flags;Registration(BroadcastReceiver r,IntentFilter f,int x){receiver=r;filter=f;flags=x;}}
 public final UsbDevice device=new UsbDevice("/dev/bus/usb/001/010",10,0x0bda,0x2838);
 public final UsbManager manager=new UsbManager();public final List<Registration> registrations=new CopyOnWriteArrayList<>();
 public int registerCalls,unregisterCalls,failRegisterAt;public BroadcastReceiver permissionReceiver;
 public FakeContext(){manager.devices.put(device.getDeviceName(),device);}
 public Object getSystemService(String n){return manager;}public String getPackageName(){return "marto.rtl_tcp_andro";}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return registerReceiver(r,f,0);}
 public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){registerCalls++;if(registerCalls==failRegisterAt)throw new SecurityException("register failure");registrations.add(new Registration(r,f,flags));if(f.action().endsWith("USB_PERMISSION"))permissionReceiver=r;return null;}
 public void unregisterReceiver(BroadcastReceiver r){unregisterCalls++;boolean removed=registrations.removeIf(x->x.receiver==r);if(!removed)throw new IllegalArgumentException("not registered");}
 public void fire(Intent i){for(Registration r:new ArrayList<>(registrations))if(r.filter.matches(i))r.receiver.onReceive(this,i);}
 public void deliver(){fire(new Intent(manager.pending.intent));}
 public void deliverQueued(PendingIntent p,BroadcastReceiver r){r.onReceive(this,new Intent(p.intent));}
}
