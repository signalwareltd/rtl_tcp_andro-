/*
 * rtl_tcp_andro is a library that uses libusb and librtlsdr to
 * turn your Realtek RTL2832 based DVB dongle into a SDR receiver.
 * It independently implements the rtl-tcp API protocol for native Android usage.
 * Copyright (C) 2022 by Signalware Ltd <driver@sdrtouch.com>
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

#ifndef COMMON_C_
#define COMMON_C_

#include <android/log.h>
#include <stddef.h>
#include <jni.h>

#define LOGI(...) ((void)common_logf(__VA_ARGS__))
#define LOGI_NATIVE(...) ((void)__android_log_print(ANDROID_LOG_INFO, "SDR", __VA_ARGS__))

#define EXCEPT_SAFE_NUM(expression) expression; if ((*env)->ExceptionCheck(env)) return 0;
#define EXCEPT_SAFE_VOID(expression) expression; if ((*env)->ExceptionCheck(env)) return;
#define EXCEPT_SAFE_GOTO(expression, label) expression; if ((*env)->ExceptionCheck(env)) goto label;
#define EXCEPT_DO(expression, action) expression; if ((*env)->ExceptionCheck(env)) action;

void throwExceptionWithInt(JNIEnv *env, const char * className, const int code);

int attachThread(JNIEnv **tEnv);
void detatchThread(int resultFromAttachThread);
void common_logf(const char *format, ...);

#endif