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

#include <stdio.h>
#include <malloc.h>
#include "common.h"

void throwExceptionWithInt(JNIEnv *env, const char * className, const int code) {
    jclass exClass = (*env)->FindClass(env, className);
    if (exClass == NULL) goto err;
    jmethodID method = (*env)->GetMethodID(env, exClass, "<init>", "(I)V");
    if (method == NULL) goto err;
    jthrowable exception = (jthrowable) (*env)->NewObject(env, exClass, method, (jint) code);
    if (exception == NULL) goto err;
    (*env)->Throw(env, exception);
    return;
err:
    LOGI("Cannot throw an exception %s with code %d!", className, code);
}

extern void initialize(JNIEnv *env);
static JavaVM *mVM = NULL;
static jclass logClass;
static jmethodID logMethod;
jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env = NULL;
    jint result = -1;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        return result;
    }
    mVM = vm;

    initialize(env);
    logClass = (jclass) (*env)->NewGlobalRef(env, (*env)->FindClass(env, "com/sdrtouch/tools/Log"));
    logMethod = (*env)->GetStaticMethodID(env, logClass, "appendLine", "(Ljava/lang/String;)V");
    return JNI_VERSION_1_6;
}

int attachThread(JNIEnv **tEnv) {
    if ((*mVM)->GetEnv(mVM, (void **)tEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        if ((*mVM)->AttachCurrentThread(mVM, tEnv, NULL) != JNI_OK) {
            return 0;
        }
        return 1;
    }
    // already attached, no need to attach or detatch
    return 2;
}

void detatchThread(int resultFromAttachThread) {
    if (resultFromAttachThread == 1) (*mVM)->DetachCurrentThread(mVM);
}

static void log(char * text) {
    JNIEnv * env;
    int res = attachThread(&env);

    __android_log_write(ANDROID_LOG_INFO, "SDR", text);

    if (env != NULL) {
        jthrowable pendingException = (*env)->ExceptionOccurred(env);
        if (pendingException != NULL) {
            (*env)->ExceptionClear(env);
        }

        jstring textJava = (*env)->NewStringUTF(env, text);
        (*env)->CallStaticVoidMethod(env, logClass, logMethod, textJava);
        (*env)->DeleteLocalRef(env, textJava);

        if (pendingException != NULL) {
            (*env)->Throw(env, pendingException);
        }
    }

    detatchThread(res);
}

void common_logf(const char *format, ...)
{
    va_list arg;

    va_start (arg, format);
    char * output;

    int allocatedBytes = vasprintf(&output, format, arg);
    if (output != NULL && allocatedBytes >= 0) {
        log(output);
        free(output);
    }

    va_end (arg);
}