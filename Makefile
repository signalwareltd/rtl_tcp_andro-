all : jni/UsbPermissionHelper.h jni/RtlTcp.h 
	ndk-build 

jni/RtlTcp.h :
	javah -classpath "bin/classes:$HOME/android-sdk-linux/platforms/android-17/android.jar:libs/android-support-v4.jar" -o jni/RtlTcp.h -jni marto.rtl_tcp_andro.core.RtlTcp

jni/UsbPermissionHelper.h :
	javah -classpath "bin/classes:$HOME/android-sdk-linux/platforms/android-17/android.jar:libs/android-support-v4.jar" -o jni/UsbPermissionHelper.h -jni marto.rtl_tcp_andro.tools.UsbPermissionHelper

clean :
	ndk-build clean
	rm -f jni/UsbPermissionHelper.h jni/RtlTcp.h 
