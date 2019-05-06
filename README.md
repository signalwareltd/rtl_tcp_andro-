# Usage

This driver implements an extension of the rtl-tcp protocol. It is fully compatible with any rtl-tcp capable client. However it also adds a number of additional commands that can allow Android clients to use the same API to control other SDR hardware devices such as the SDRplay or HackRF.

Here are the steps you need to take when building your app so you can start receiving I/Q samples and control the tuner.

## Invoke intent

Create an intent that starts with *iqsrc://*. The arguments are compatible with arguments that can be provided to *rtl_tcp*. You can find the supported arguments in [SdrTcpArguments.java](app/src/main/java/com/sdrtouch/core/SdrTcpArguments.java).

A simple example would be starting with a samplerate and a port number like this:

```java
Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse("iqsrc://-a 127.0.0.1 -p "+ port + " -s "+ samplerate));
startActivityForResult(intent, 1234);
```

*Note:* It is possible that the user doesn't have the driver installed and this invocation can throw a [ActivityNotFound](https://developer.android.com/reference/android/content/ActivityNotFoundException.html) exception. In such a case you should ask the user to consider installing the RTL-SDR + HackRF driver from https://play.google.com/store/apps/details?id=marto.rtl_tcp_andro or http://www.amazon.com/gp/mas/dl/android?p=marto.rtl_tcp_andro .

*Advanced note:* There might be multiple sdr drivers installed on the device that can handle this protocol (ex. if the user has the SDRplay driver at the same time). This will mean the user will be asked to chose which driver to use. If they click the "always use this app" option in the chooser, they will be stuck with only one of the drivers being called for the *iqsrc* intent, leaving the other driver idle forever. To avoid this poor user experience, your app should use [PackageManager::queryIntentActivities](https%3A%2F%2Fdeveloper.android.com%2Freference%2Fandroid%2Fcontent%2Fpm%2FPackageManager.html%23queryIntentActivities(android.content.Intent%2C%20int)) to enumerate installed apps that support *iqsrc*. Your app can then go trough the list and ask each of them to open a device until it finds one that returns *RESULT_OK*.

## Handle response

```java
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != 1234) return; // This is the requestCode that was used with startActivityForResult
        if (resultCode == RESULT_OK) {
           // Connection with device has been opened and the rtl-tcp server is running. You are now responsible for connecting.
           int[] supportedTcpCommands = data.getIntArrayExtra("supportedTcpCommands");
           startTcp(supportedTcpCommands); // Start your TCP client, see section below
        } else {
           // something went wrong, and the driver failed to start
           String errmsg = data.getStringExtra("detailed_exception_message");
           showErrorMessage(errmsg); // Show the user why something went wrong
        }
    }
```

* `supportedTcpCommands` contains a list of TCP commands that are supported. Information about them could be found in [tcp_commands.h](app/src/main/jni/tcp_commands.h). This value may not be set on old versions of the driver, so keep that in mind!
* `errmsg` is an explanation of why the driver did not start in English. The reason is also passed as an integer in  `detailed_exception_code` for programmatic access. More information in [DeviceOpenActivity.java](app/src/main/java/com/sdrtouch/rtlsdr/DeviceOpenActivity.java).

## Auto start

The driver will emmit `com.sdrtouch.rtlsdr.SDR_DEVICE_ATTACHED` action when a compatible USB tuner has been connected to the Android device. You can use this intent to launch your application automatically.

## TCP client

Once a *RESULT_OK* has been returned, you have to connect to the driver with a TCP client using the localhost and the port you have provided. Your client will start receiving IQ samples. By default each sample consists of two 8 bit unsigned bytes - one for the I and one for the Q part of the sample. Now you can use these samples for whatever reason you like!

If you want to control the tuner, such as set the central frequency, you can send TCP commands consisting of an unsigned 8 bit byte control code (defined in [tcp_commands.h](app/src/main/jni/tcp_commands.h)) bundled with a 32 bit unsigned parameter. Most rtl-tcp commands are supported, however there are a couple of additional Android specific ones.

*Note:* Non-rtl-tcp drivers (such as the SDRplay one) can only support a sub-set of the rtl-tcp commands. You can learn about the list of supported commands via the `supportedTcpCommands` array. Drivers like SDRplay can also support extended commands for additional features, ex. such as 16 bit sample size. You must use this array to learn whether a command is supported before using it, otherwise it will have no effect.

# Compatible apps

* [SDR Touch (Google Play)](https://play.google.com/store/apps/details?id=marto.androsdr2)
* [SDR Touch (Amazon)](http://www.amazon.com/gp/mas/dl/android?p=marto.androsdr2.a)
* [Wavesink DAB/FM](https://play.google.com/store/apps/details?id=de.ses.wavesink)
* [RF Analyzer](https://play.google.com/store/apps/details?id=com.mantz_it.rfanalyzer)
* [welle.io](https://play.google.com/store/apps/details?id=io.welle.welle)
* [ADSB Flight Tracker](https://play.google.com/store/apps/details?id=com.enthusiasticcoder.adsbflightmonitor)

Open a github issue if you want your app to be featured here!

# License

This is a modification of rtl_tcp and libusb-1.0 for running on Android (also works on other Linux based systems) 
 
It is compatible with the original rtl_tcp with the following exceptions: 
 
Added features: 
 - Opening devices using a file descriptor (very important for Android).
 - An additional command for closing the app remotely and setting tuner gain in percentage

 Removed features:
 - Ability to automatically open a device in JNI (without first opening it via the Android USB API)

Files modified in libusb-1.0 to create libusb-andro: 
 core.c, libusb.h and libusbi.h - to header for and implementation of the open2 function which takes an already open file descriptor 
 linux_usbfs.c - to create a libusb handle from the fd 

The modifications are released under GNU. See [COPYING](/COPYING) for details. 
 
For more information on rtl-sdr: 
http://sdr.osmocom.org/trac/wiki/rtl-sdr 
 
For more information on libusb: 
http://www.libusb.org/wiki/libusb-1.0

Based on modifications on libusb:
https://github.com/kuldeepdhaka/libusb

Hack RF support is based on Dennis Mantz's HackRF for Android:
https://github.com/demantz/hackrf_android

Hack RF library for Android is based on:
https://github.com/mossmann/hackrf/tree/master/host/libhackrf
