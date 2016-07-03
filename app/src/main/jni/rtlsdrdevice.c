/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2016 by Martin Marinov <martintzvetomirov@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <jni.h>
#include <stdlib.h>
#include "common.h"
#include "rtl-sdr-android.h"
#include "sdrtcp.h"
#include "SdrException.h"
#include "tcp_commands.h"

#define RUN_OR(command, exit_command) { \
    int cmd_result = command; \
    if (cmd_result != 0) { \
        throwExceptionWithInt(env, "com/sdrtouch/core/exceptions/SdrException", cmd_result); \
        exit_command; \
    }; \
}

#define RUN_OR_GOTO(command, label) RUN_OR(command, goto label);

typedef struct rtlsdr_android {
    sdrtcp_t tcpserv;
    rtlsdr_dev_t * rtl_dev;
} rtlsdr_android_t;

#define WITH_DEV(x) rtlsdr_android_t* x = (rtlsdr_android_t*) pointer

void initialize(JNIEnv *env) {
    LOGI_NATIVE("Initializing");
}

static int set_gain_by_index(rtlsdr_dev_t *_dev, unsigned int index)
{
    int res = 0;
    int* gains;
    int count = rtlsdr_get_tuner_gains(_dev, NULL);

    if (count > 0 && (unsigned int)count > index) {
        gains = malloc(sizeof(int) * count);
        rtlsdr_get_tuner_gains(_dev, gains);

        res = rtlsdr_set_tuner_gain(_dev, gains[index]);

        free(gains);
    }

    return res;
}

static int set_gain_by_perc(rtlsdr_dev_t *_dev, unsigned int percent)
{
    int res = 0;
    int* gains;
    int count = rtlsdr_get_tuner_gains(_dev, NULL);
    unsigned int index = (percent * count) / 100;
    if (index < 0) index = 0;
    if (index >= (unsigned int) count) index = (unsigned int) (count - 1);

    gains = malloc(sizeof(int) * count);
    rtlsdr_get_tuner_gains(_dev, gains);

    res = rtlsdr_set_tuner_gain(_dev, gains[index]);

    free(gains);

    return res;
}

static jint SUPPORTED_COMMANDS[] = {
        TCP_SET_FREQ,
        TCP_SET_SAMPLE_RATE,
        TCP_SET_GAIN_MODE,
        TCP_SET_GAIN,
        TCP_SET_FREQ_CORRECTION,
        TCP_SET_IF_TUNER_GAIN,
        TCP_SET_TEST_MODE,
        TCP_SET_AGC_MODE,
        TCP_SET_DIRECT_SAMPLING,
        TCP_SET_OFFSET_TUNING,
        TCP_SET_RTL_XTAL,
        TCP_SET_TUNER_XTAL,
        TCP_SET_TUNER_GAIN_BY_ID,
        TCP_ANDROID_EXIT,
        TCP_ANDROID_GAIN_BY_PERCENTAGE
};

void tcpCommandCallback(sdrtcp_t * tcpserv, void * pointer, sdr_tcp_command_t * cmd) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;

    switch(cmd->command) {
        case TCP_SET_FREQ:
            rtlsdr_set_center_freq(dev->rtl_dev,cmd->parameter);
            break;
        case TCP_SET_SAMPLE_RATE:
            LOGI("set sample rate %ld", cmd->parameter);
            rtlsdr_set_sample_rate(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_GAIN_MODE:
            LOGI("set gain mode %ld", cmd->parameter);
            rtlsdr_set_tuner_gain_mode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_GAIN:
            LOGI("set gain %ld", cmd->parameter);
            rtlsdr_set_tuner_gain(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_FREQ_CORRECTION:
            LOGI("set freq correction %ld", cmd->parameter);
            rtlsdr_set_freq_correction(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_IF_TUNER_GAIN:
            rtlsdr_set_tuner_if_gain(dev->rtl_dev, cmd->parameter >> 16, (short)(cmd->parameter & 0xffff));
            break;
        case TCP_SET_TEST_MODE:
            LOGI("set test mode %ld", cmd->parameter);
            rtlsdr_set_testmode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_AGC_MODE:
            LOGI("set agc mode %ld", cmd->parameter);
            rtlsdr_set_agc_mode(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_DIRECT_SAMPLING:
            LOGI("set direct sampling %ld", cmd->parameter);
            rtlsdr_set_direct_sampling(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_OFFSET_TUNING:
            LOGI("set offset tuning %ld", cmd->parameter);
            rtlsdr_set_offset_tuning(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_SET_RTL_XTAL:
            LOGI("set rtl xtal %d", cmd->parameter);
            rtlsdr_set_xtal_freq(dev->rtl_dev, cmd->parameter, 0);
            break;
        case TCP_SET_TUNER_XTAL:
            LOGI("set tuner xtal %dl", cmd->parameter);
            rtlsdr_set_xtal_freq(dev->rtl_dev, 0, cmd->parameter);
            break;
        case TCP_SET_TUNER_GAIN_BY_ID:
            LOGI("set tuner gain by index %d", cmd->parameter);
            set_gain_by_index(dev->rtl_dev, cmd->parameter);
            break;
        case TCP_ANDROID_EXIT:
            LOGI("tcpCommandCallback: client requested to close rtl_tcp_andro");
            sdrtcp_stop_serving_client(tcpserv);
            break;
        case TCP_ANDROID_GAIN_BY_PERCENTAGE:
            set_gain_by_perc(dev->rtl_dev, cmd->parameter);
            break;
        default:
            // don't forget to add any new commands into SUPPORTED_COMMANDS!
            break;
    }
}

void tcpClosedCallback(sdrtcp_t * tcpserv, void * pointer) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;
    rtlsdr_cancel_async(dev->rtl_dev);
}

void rtlsdr_callback(unsigned char *buf, uint32_t len, void *pointer) {
    WITH_DEV(dev);
    if (dev->rtl_dev == NULL) return;
    sdrtcp_feed(&dev->tcpserv, buf, len / 2);
}

JNIEXPORT jboolean JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_openAsync__JIIJJIILjava_lang_String_2Ljava_lang_String_2(
        JNIEnv *env, jobject instance, jlong pointer, jint fd, jint gain, jlong samplingrate,
        jlong frequency, jint port, jint ppm, jstring address_, jstring devicePath_) {
    WITH_DEV(dev);
    const char *devicePath = (*env)->GetStringUTFChars(env, devicePath_, 0);
    const char *address = (*env)->GetStringUTFChars(env, address_, 0);

    EXCEPT_SAFE_NUM(jclass clazz = (*env)->GetObjectClass(env, instance));
    EXCEPT_SAFE_NUM(jmethodID announceOnOpen = (*env)->GetMethodID(env, clazz, "announceOnOpen", "()V"));

    rtlsdr_dev_t * device = NULL;
    RUN_OR_GOTO(rtlsdr_open2(&device, fd, devicePath), rel_jni);

    if (ppm != 0) {
        if (rtlsdr_set_freq_correction(device, ppm) < 0) {
            LOGI("WARNING: Failed to set ppm to %d", ppm);
        }
    }

    int result = 0;
    if (samplingrate < 0 || (result = rtlsdr_set_sample_rate(device, (uint32_t) samplingrate)) < 0) {
        LOGI("ERROR: Failed to set sample rate to %lld", samplingrate);
        // LIBUSB_ERROR_IO is -1
        // LIBUSB_ERROR_TIMEOUT is -7
        if (result == -1 || result == -7) {
            RUN_OR(EXIT_NOT_ENOUGH_POWER, goto err);
        } else {
            RUN_OR(EXIT_WRONG_ARGS, goto err);
        }
    } else {
        LOGI("Set sampling rate to %lld", samplingrate);
    }

    if (frequency < 0 || rtlsdr_set_center_freq(device, (uint32_t) frequency) < 0) {
        LOGI("ERROR: Failed to frequency to %lld", frequency);
        RUN_OR(EXIT_WRONG_ARGS, goto err);
    }

    if (0 == gain) {
        if (rtlsdr_set_tuner_gain_mode(device, 0) < 0)
            LOGI("WARNING: Failed to enable automatic gain");
    } else {
        /* Enable manual gain */
        if (rtlsdr_set_tuner_gain_mode(device, 1) < 0)
            LOGI("WARNING: Failed to enable manual gain");

        if (rtlsdr_set_tuner_gain(device, gain) < 0)
            LOGI("WARNING: Failed to set tuner gain");
        else
            LOGI("Tuner gain set to %f dB", gain/10.0);
    }

    if (rtlsdr_reset_buffer(device) < 0)
        LOGI("WARNING: Failed to reset buffers");

    if (!sdrtcp_open_socket(&dev->tcpserv, address, port, "RTL0", rtlsdr_get_tuner_type(device), (uint32_t) rtlsdr_get_tuner_gains(device, NULL))) {
        RUN_OR(EXIT_WRONG_ARGS, goto err);
    }

    dev->rtl_dev = device;
    sdrtcp_serve_client_async(&dev->tcpserv, (void *) dev, tcpCommandCallback, tcpClosedCallback);

    int succesful = 1;
    EXCEPT_DO((*env)->CallVoidMethod(env, instance, announceOnOpen), succesful  = 0);
    if (rtlsdr_read_async(device, rtlsdr_callback, (void *) dev, 0, 0)) {
        LOGI("rtlsdr_read_async failed");
        succesful = 0;
    }
    LOGI("rtlsdr_read_async finished successfully");

    dev->rtl_dev = NULL;
    rtlsdr_close(device);
    sdrtcp_stop_serving_client(&dev->tcpserv);

    (*env)->ReleaseStringUTFChars(env, address_, address);
    (*env)->ReleaseStringUTFChars(env, devicePath_, devicePath);

    return succesful ? ((jboolean) JNI_TRUE) : ((jboolean) JNI_FALSE);

err:
    rtlsdr_close(device);

rel_jni:
    (*env)->ReleaseStringUTFChars(env, address_, address);
    (*env)->ReleaseStringUTFChars(env, devicePath_, devicePath);

    return (jboolean) JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_initialize(JNIEnv *env, jobject instance) {
    rtlsdr_android_t* ptr = malloc(sizeof(rtlsdr_android_t));
    sdrtcp_init(&ptr->tcpserv);
    ptr->rtl_dev = NULL;
    return (jlong) ptr;
}

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_deInit(JNIEnv *env, jobject instance, jlong pointer) {
    WITH_DEV(dev);
    sdrtcp_free(&dev->tcpserv);
    if (dev->rtl_dev != NULL) {
        rtlsdr_close(dev->rtl_dev);
        dev->rtl_dev = NULL;
    }
    free((void *) dev);
}

JNIEXPORT void JNICALL
Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_close__J(JNIEnv *env, jobject instance,
                                                      jlong pointer) {
    WITH_DEV(dev);
    sdrtcp_stop_serving_client(&dev->tcpserv);
}

JNIEXPORT jobjectArray JNICALL Java_com_sdrtouch_rtlsdr_driver_RtlSdrDevice_getSupportedCommands(JNIEnv *env, jobject instance) {
    jint * commands = (jint *) SUPPORTED_COMMANDS;
    int n_commands = sizeof(SUPPORTED_COMMANDS) / sizeof(SUPPORTED_COMMANDS[0]);

    jintArray result;
    result = (*env)->NewIntArray(env, n_commands);
    if (result == NULL) return NULL;

    (*env)->SetIntArrayRegion(env, result, 0, n_commands, commands);
    return result;
}