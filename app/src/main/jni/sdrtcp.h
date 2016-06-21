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

#ifndef TCP_SDR_C_
#define TCP_SDR_C_

#include <stdint.h>
#include "workpool.h"
#include "threading.h"

#define DESIRED_MAX_NUMBER_OF_SAMPLES (32767)

typedef struct sdr_tcp_command{
    uint8_t command;
    uint32_t parameter;
}__attribute__((packed)) sdr_tcp_command_t;

typedef struct sdrtcp sdrtcp_t;

typedef void (*sdrtcp_command_callback)(sdrtcp_t *, void * ctx, sdr_tcp_command_t *);
typedef void (*sdrtcp_closed_callback)(sdrtcp_t *, void * ctx);

struct sdrtcp {
    volatile int state;
    pthread_mutex_t state_locker;
    workpool_t workpool;

    sdrtcp_closed_callback closedcb;
    sdrtcp_command_callback commandcb;
    void * ctx;

    volatile int client_socket;
    volatile int listen_socket;
};

void sdrtcp_init(sdrtcp_t * obj);
void sdrtcp_free(sdrtcp_t * obj);

// Opens socket. If this returns true then we are ready to wait for client
// dongleMagic must be exactly 4 characters long!
int sdrtcp_open_socket(sdrtcp_t * obj, const char * address, int port, const char * dongleMagic, uint32_t dongleType, uint32_t gainsCount);


// Starts waiting for and feeding a client asynchroneously
// if a command is received command cb will be called (guaranteed in a separate thread). This callback can be called after sdrtcp_stop_serving_client!!!
// if for any reason the server stops, closedcb will be called (in a separate thread unless a config error)
void sdrtcp_serve_client_async(sdrtcp_t * obj, void * ctx, sdrtcp_command_callback commandcb, sdrtcp_closed_callback closedcb);

// doesn't block
void sdrtcp_stop_serving_client(sdrtcp_t * obj);

// queue up data to send over the connection
// return 0 if there was an error and this function should not be called anymore until another sdrtcp_open_socket
int sdrtcp_feed(sdrtcp_t * obj, unsigned char * buf, uint32_t len);

#endif