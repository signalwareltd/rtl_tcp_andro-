#!/bin/bash

rm -f jni/RtlTcp.h
javah -classpath bin/classes:$HOME/android-sdks/platforms/android-19/android.jar:libs/android-support-v4.jar -o jni/RtlTcp.h -jni marto.rtl_tcp_andro.core.RtlTcp
