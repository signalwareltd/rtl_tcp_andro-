#!/bin/bash

javah -classpath "bin/classes:$HOME/android-sdk-linux/platforms/android-17/android.jar:libs/android-support-v4.jar" -d jni -jni marto.rtl_tcp_andro.tools.UsbPermissionHelper
