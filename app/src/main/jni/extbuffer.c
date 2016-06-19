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

#include <stddef.h>
#include <assert.h>
#include <stdlib.h>
#include <jni.h>
#include "extbuffer.h"

void extbuffer_init(extbuffer_t * container, int type) {
	container->type = type;
	container->intbuffer = NULL;
	container->floatbuffer = NULL;
	container->shortbuffer = NULL;
	container->ushortbuffer = NULL;
	container->charbuffer = NULL;
	container->buffer_max_size = 0;
	container->size_valid_elements = 0;

	container->valid = 0;
	container->cleartozero = 1;
}

void extbuffer_preparetohandle(extbuffer_t * container, int size) {
	if (size <= 0) {
		container->size_valid_elements = 0;
		return;
	}

	if (container->buffer_max_size < size) {

		switch (container->type) {
		case EXTBUFF_TYPE_INT:
			if (container->intbuffer == NULL) {
				container->intbuffer = (jint *) malloc(sizeof(jint) * size);
				container->valid = 1;
			} else if (container->buffer_max_size != size)
				container->intbuffer = (jint *) realloc((void *) container->intbuffer, sizeof(jint) * size);

			break;
		case EXTBUFF_TYPE_FLOAT:
			if (container->floatbuffer == NULL) {
				container->floatbuffer = (jfloat *) malloc(sizeof(jfloat) * size);
				container->valid = 1;
			} else if (container->buffer_max_size != size)
				container->floatbuffer = (jfloat *) realloc((void *) container->floatbuffer, sizeof(jfloat) * size);

			break;
		case EXTBUFF_TYPE_CHAR:
			if (container->charbuffer == NULL) {
				container->charbuffer = (char *) malloc(sizeof(char) * size);
				container->valid = 1;
			} else if (container->buffer_max_size != size)
				container->charbuffer = (char *) realloc((void *) container->charbuffer, sizeof(char) * size);

			break;
		case EXTBUFF_TYPE_SHORT:
				if (container->shortbuffer == NULL) {
					container->shortbuffer = (int16_t *) malloc(sizeof(int16_t) * size);
					container->valid = 1;
				} else if (container->buffer_max_size != size)
					container->shortbuffer = (int16_t *) realloc((void *) container->shortbuffer, sizeof(int16_t) * size);

				break;
		case EXTBUFF_TYPE_USHORT:
			if (container->ushortbuffer == NULL) {
				container->ushortbuffer = (uint16_t *) malloc(sizeof(uint16_t) * size);
				container->valid = 1;
			} else if (container->buffer_max_size != size)
				container->ushortbuffer = (uint16_t *) realloc((void *) container->ushortbuffer, sizeof(uint16_t) * size);

			break;
		}


		container->buffer_max_size = size;
	}

	container->size_valid_elements = size;
	if (container->cleartozero) {
		int i;

		switch (container->type) {
			case EXTBUFF_TYPE_INT:
				for (i = 0; i < container->size_valid_elements; i++)
					container->intbuffer[i] = 0;
				break;
			case EXTBUFF_TYPE_FLOAT:
				for (i = 0; i < container->size_valid_elements; i++)
					container->floatbuffer[i] = 0.0f;
				break;
			case EXTBUFF_TYPE_CHAR:
				for (i = 0; i < container->size_valid_elements; i++)
					container->charbuffer[i] = 0;
				break;
			case EXTBUFF_TYPE_SHORT:
				for (i = 0; i < container->size_valid_elements; i++)
					container->shortbuffer[i] = 0;
				break;
			case EXTBUFF_TYPE_USHORT:
				for (i = 0; i < container->size_valid_elements; i++)
					container->ushortbuffer[i] = 0;
				break;
		}


		container->cleartozero = 0;
	}

}

void extbuffer_cleartozero(extbuffer_t * container) {
	container->cleartozero = 1;
}

void extbuffer_free(extbuffer_t * container) {

	container->valid = 0;

	if (container->intbuffer != NULL) {
		jint * intbuff = container->intbuffer;
		container->intbuffer = NULL;
		free(intbuff);
	}

	if (container->floatbuffer != NULL) {
		jfloat * floatbuffer = container->floatbuffer;
		container->floatbuffer = NULL;
		free(floatbuffer);
	}

	if (container->charbuffer != NULL) {
		char * charbuffer = container->charbuffer;
		container->charbuffer = NULL;
		free(charbuffer);
	}

	if (container->shortbuffer != NULL) {
		int16_t * shortbuffer = container->shortbuffer;
		container->shortbuffer = NULL;
		free(shortbuffer);
	}

	if (container->ushortbuffer != NULL) {
		uint16_t * shortbuffer = container->ushortbuffer;
		container->ushortbuffer = NULL;
		free(shortbuffer);
	}
}
