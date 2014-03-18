LOCAL_PATH:= $(call my-dir)
APP_PLATFORM:= android-8
APP_ABI:= armeabi armeabi-v7a x86

include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
 libusb-andro/libusb/core.c \
 libusb-andro/libusb/descriptor.c \
 libusb-andro/libusb/io.c \
 libusb-andro/libusb/sync.c \
 libusb-andro/libusb/os/linux_usbfs.c \
 libusb-andro/libusb/os/threads_posix.c\
 RtlTcp.c \
 rtl_tcp_andro.c \
 rtl-sdr/src/convenience/convenience.c \
 librtlsdr_andro.c \
 rtl-sdr/src/tuner_e4k.c \
 rtl-sdr/src/tuner_fc0012.c \
 rtl-sdr/src/tuner_fc0013.c \
 rtl-sdr/src/tuner_fc2580.c \
 rtl-sdr/src/tuner_r82xx.c

LOCAL_C_INCLUDES += \
jni/libusb-andro \
jni/libusb-andro/libusb \
jni/libusb-andro/libusb/os \
jni/libusb-andro/libusb \
jni/rtl-sdr/include \
jni/rtl-sdr/src

LOCAL_CFLAGS += -DLIBUSB_DESCRIBE="" -O3 -fno-builtin-printf -fno-builtin-fprintf
LOCAL_MODULE:= RtlTcp
LOCAL_PRELINK_MODULE:= true
include $(BUILD_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := UsbPermissionHelper
LOCAL_LDLIBS := -llog
LOCAL_SRC_FILES := UsbPermissionHelper.c
include $(BUILD_SHARED_LIBRARY)
