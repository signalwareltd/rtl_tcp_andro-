EXE=rtl_tcp_andro

SRC = \
 jni/libusb-andro/libusb/core.c \
 jni/libusb-andro/libusb/descriptor.c \
 jni/libusb-andro/libusb/io.c \
 jni/libusb-andro/libusb/sync.c \
 jni/libusb-andro/libusb/os/linux_usbfs.c \
 jni/libusb-andro/libusb/os/threads_posix.c\
 jni/rtl_tcp_andro.c \
 jni/librtlsdr_andro.c \
 jni/rtl-sdr/src/tuner_e4k.c \
 jni/rtl-sdr/src/tuner_fc0012.c \
 jni/rtl-sdr/src/tuner_fc0013.c \
 jni/rtl-sdr/src/tuner_fc2580.c \
 jni/rtl-sdr/src/tuner_r820t.c

INCLUDES = \
-I jni \
-I jni/libusb-andro \
-I jni/rtl-sdr/include \
-I jni/rtl-sdr/src \
-I jni/libusb-andro/libusb \
-I libusb-andro/libusb/os

LIB=-lpthread -lrt

COMPILER = g++
CFLAGS = -DLIBUSB_DESCRIBE=""

all:
	$(CC) $(INCLUDES) $(CFLAGS) -o $(EXE) $(SRC) $(LIB)
	
clean:
	rm -rf $(EXE)

