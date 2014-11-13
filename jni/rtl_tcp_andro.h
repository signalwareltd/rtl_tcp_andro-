/*
 * rtl_tcp_andro is an Android port of the famous rtl_tcp driver for
 * RTL2832U based USB DVB-T dongles. It does not require root.
 * Copyright (C) 2012 by Martin Marinov <martintzvetomirov@gmail.com>
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

#ifndef RTL_TCP_ANDRO_H_
#define RTL_TCP_ANDRO_H_

#include <android/log.h>

#ifndef LOGI
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "rtl_tcp_andro", __VA_ARGS__))
#endif

void rtltcp_main(int usbfd, const char * uspfs_path_input, int argc, char **argv);
void rtltcp_close();
int rtltcp_isrunning();

void aprintf( const char* format , ... );
void aprintf_stderr( const char* format , ... );
void announce_exceptioncode( const int exception_code );
void announce_success( );
void thread_detach();


#endif
