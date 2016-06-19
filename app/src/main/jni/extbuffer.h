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

#ifndef EXTBUFFER_H_
#define EXTBUFFER_H_

#define EXTBUFF_TYPE_INT (0)
#define EXTBUFF_TYPE_FLOAT (1)
#define EXTBUFF_TYPE_CHAR (2)
#define EXTBUFF_TYPE_SHORT (3)
#define EXTBUFF_TYPE_USHORT (4)

#include "stdint.h"
#include <jni.h>

// enable for debugging purposes
#define SHOW_EXTBUFF_USAGE (0)

typedef struct extbuffer {

	jint * intbuffer;
	jfloat * floatbuffer;
	char * charbuffer;
	int16_t * shortbuffer;
	uint16_t * ushortbuffer;

	int buffer_max_size;
	int type;

	int size_valid_elements;

	volatile int valid;
	volatile int cleartozero;

} extbuffer_t;

void extbuffer_init(extbuffer_t * container, int type);
void extbuffer_preparetohandle(extbuffer_t * container, int size);
void extbuffer_cleartozero(extbuffer_t * container);
void extbuffer_free(extbuffer_t * container);


#endif /* EXTBUFFER_H_ */
