cmake_minimum_required(VERSION 3.13)

PROJECT("libusb" C)

# libusb 1.0.30 does not ship a CMake build. This wrapper mirrors the
# maintained Android ndk-build file (android/jni/libusb.mk) so that the
# library can be built as part of the rtlSdrAndroid CMake project.
SET(LIBUSB_SOURCES
    libusb/core.c
    libusb/descriptor.c
    libusb/hotplug.c
    libusb/io.c
    libusb/sync.c
    libusb/strerror.c
    libusb/os/linux_usbfs.c
    libusb/os/events_posix.c
    libusb/os/threads_posix.c
    libusb/os/linux_netlink.c
)

FIND_LIBRARY( LOG_LIB log )

ADD_LIBRARY( ${PROJECT_NAME} SHARED ${LIBUSB_SOURCES} )
TARGET_INCLUDE_DIRECTORIES( ${PROJECT_NAME}
    PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/android"
            "${CMAKE_CURRENT_SOURCE_DIR}/libusb/os"
    PUBLIC  "${CMAKE_CURRENT_SOURCE_DIR}/libusb"
)
TARGET_COMPILE_OPTIONS( ${PROJECT_NAME} PRIVATE -Wall -fvisibility=hidden -pthread )
TARGET_LINK_LIBRARIES( ${PROJECT_NAME} PRIVATE ${LOG_LIB} )

TARGET_LINK_OPTIONS(${PROJECT_NAME} PRIVATE
    "-Wl,-z,max-page-size=16384"
    "-Wl,-z,common-page-size=16384"
)
