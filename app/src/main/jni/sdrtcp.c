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

#include <pthread.h>
#include <unistd.h>
#include <arpa/inet.h>

#include "sdrtcp.h"
#include "common.h"
#include "extbuffer.h"

#define FEED_SLEEP_IF_NOT_READY_MILLIS (500)

#define POOL_MAX_ELEMENTS (5)

#define STAGE_UNINITIALIZED (0)
#define STAGE_INITIALIZED (1)
#define STAGE_SOCKET_OPEN (2)
#define STAGE_CLIENT_OPEN (3)
#define STAGE_CLIENT_OPEN_STARTED_ASYNC (4)
#define STAGE_CLIENT_SERVING (5)
#define STAGE_NEEDS_STOPPING (6)

#define RETURN_FAILURE { sdrtcp_cleanup(obj); return 0; }
#define RETURN_SUCCESS { return 1; }
#define RETURN_AND_CLOSE { sdrtcp_cleanup(obj); return; }

typedef struct {
    char magic[4];
    uint32_t dongleType;
    uint32_t gainsCount;
} dongle_info_t;

static void sdrtcp_cleanup(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state != STAGE_UNINITIALIZED) {
        obj->state = STAGE_UNINITIALIZED;

        LOGI("SdrTcp: Closing from state %d", obj->state);

        pool_free(&obj->workpool);
        if (obj->state != STAGE_INITIALIZED && obj->listen_socket != -1) {
            close(obj->listen_socket);
        }

        obj->client_socket = -1;
        obj->listen_socket = -1;
    }
    pthread_mutex_unlock(&obj->state_locker);
}

static void commandListener(void *arg) {
    sdrtcp_t * obj = (sdrtcp_t *) arg;

    size_t left = 0;
    fd_set readfds;
    sdr_tcp_command_t cmd={0, 0};
    struct timeval tv= {1, 0};

    while(obj->state == STAGE_CLIENT_SERVING) {
        left = sizeof(cmd);
        while (left > 0 && obj->state == STAGE_CLIENT_SERVING) {
            FD_ZERO(&readfds);
            FD_SET(obj->client_socket, &readfds);
            tv.tv_sec = 1;
            tv.tv_usec = 0;
            int r = 0;

            if (obj->state == STAGE_CLIENT_SERVING) r = select(obj->client_socket + 1, &readfds, NULL, NULL, &tv);

            if (obj->state == STAGE_CLIENT_SERVING && r) {
                ssize_t received = recv(obj->client_socket, (char *) &cmd + (sizeof(cmd) - left), left, 0);
                left -= received;

                if (received == -1) {
                    LOGI("SdrTcp: commandListener failed to receive command");
                    obj->state = STAGE_NEEDS_STOPPING;
                    break;
                }
            }
        }

        if (obj->state == STAGE_CLIENT_SERVING && left == 0) {
            cmd.parameter = ntohl(cmd.parameter);
            obj->commandcb(obj, obj->ctx, &cmd);
            cmd.command = 0xff;
        }
    }

    LOGI("SdrTcp: Command listener thread exiting");
    pthread_exit(NULL);
}

static void serveClient(sdrtcp_t * obj) {
    LOGI("SdrTcp: Client has connected.");

    struct timeval tv= {1,0};
    fd_set writefds;

    while (obj->state == STAGE_CLIENT_SERVING) {
        extbuffer_t * buff;
        if (obj->state == STAGE_CLIENT_SERVING) {
            if ((buff = pool_get_wait_lock(&obj->workpool, 1, 1)) == NULL) {
                continue;
            }
        } else {
            break;
        }

        size_t index = 0;
        ssize_t bytessent = -1;
        size_t bytesleft = sizeof(uint16_t) * buff->size_valid_elements;
        uint8_t * data_to_send = (uint8_t *) buff->ushortbuffer;

        while(bytesleft > 0 && obj->state == STAGE_CLIENT_SERVING) {
            FD_ZERO(&writefds);
            FD_SET(obj->client_socket, &writefds);
            tv.tv_sec = 1;
            tv.tv_usec = 0;

            int r = 0;
            if (obj->state == STAGE_CLIENT_SERVING) r = select(obj->client_socket+1, NULL, &writefds, NULL, &tv);

            if(r && obj->state == STAGE_CLIENT_SERVING) {
                bytessent = send(obj->client_socket,  data_to_send + index, bytesleft, 0);
                bytesleft -= bytessent;
                index += bytessent;
            }

            if(bytessent == -1 && obj->state == STAGE_CLIENT_SERVING) {
                LOGI("SdrTcp: serveClient cannot send to client");
                obj->state = STAGE_NEEDS_STOPPING;
            }
        }

        pool_get_unlock(&obj->workpool, 1, buff);
    }
}

static void sdrtcp_wait_for_client(sdrtcp_t * obj) {
    if (obj->state != STAGE_CLIENT_OPEN_STARTED_ASYNC) return;

    struct timeval tv = {1,0};
    fd_set readfds;
    struct sockaddr_in remote;

    obj->client_socket = -1;
    while(obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {

        int failure = 0;
        FD_ZERO(&readfds);
        FD_SET(obj->listen_socket, &readfds);
        tv.tv_sec = 1;
        tv.tv_usec = 0;

        int r = 0;
        if (obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {
            r = select(obj->listen_socket + 1, &readfds, NULL, NULL, &tv);
        }

        if (r && obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {
            socklen_t rlen = sizeof(remote);
            obj->client_socket = accept(obj->listen_socket, (struct sockaddr *) &remote, &rlen);
            if (obj->client_socket != -1) {
                obj->state = STAGE_CLIENT_OPEN;
            } else {
                LOGI("SdrTcp: Failed to talk to client");
                failure = 1;
            }
        }

        if (failure) return;
    }
}

static void tcp_server(void *arg) {
    sdrtcp_t * obj = (sdrtcp_t *) arg;

    LOGI("SdrTcp: Waiting for client...");
    sdrtcp_wait_for_client(obj);

    if (obj->state == STAGE_CLIENT_OPEN) {
        LOGI("SdrTcp: TCP server succesfully started and listening for clients!");
        pthread_t commandThread;

        pthread_attr_t attrs;
        pthread_attr_init(&attrs);
        pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_JOINABLE);
        pthread_create(&commandThread, &attrs, (void *) commandListener, (void *) obj);

        pthread_mutex_lock(&obj->state_locker);
        obj->state = STAGE_CLIENT_SERVING;
        pthread_mutex_unlock(&obj->state_locker);

        serveClient(obj);

        LOGI("SdrTcp: Waiting for command thread to die");
        void *status;
        pthread_join(commandThread, &status);
    }

    LOGI("SdrTcp: TCP server shutting down.");
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state != STAGE_UNINITIALIZED) {
        pthread_mutex_unlock(&obj->state_locker);
        LOGI("SdrTcp: Closing sdrtcp due to main thread finishing");
        sdrtcp_cleanup(obj);
    } else {
        pthread_mutex_unlock(&obj->state_locker);
    }

    obj->closedcb(obj, obj->ctx);

    LOGI("SdrTcp: Server thread shut down");
    pthread_exit(NULL);
}

int sdrtcp_open_socket(sdrtcp_t * obj, const char * address, int port, const char * dongleMagic, uint32_t dongleType, uint32_t gainsCount) {
    if (obj->state != STAGE_UNINITIALIZED) {
        LOGI("SdrTcp: Called sdrtcp_open_socket with unexpected state %d", obj->state);
        RETURN_FAILURE;
    }

    obj->listen_socket = -1;

    pthread_mutex_lock(&obj->state_locker);
    pool_init(&obj->workpool, POOL_MAX_ELEMENTS, EXTBUFF_TYPE_USHORT);
    pool_set_threads(&obj->workpool, 2);

    dongle_info_t dongle_info;
    memset(&dongle_info, 0, sizeof(dongle_info));
    memcpy(&dongle_info.magic, dongleMagic, 4);
    dongle_info.dongleType = htonl(dongleType);
    dongle_info.gainsCount = htonl(gainsCount);

    // Send the dongle info as the first thing
    extbuffer_t * buff = NULL;
    if ((buff = pool_get_wait_lock(&obj->workpool, 0, 1)) != NULL) {
        extbuffer_preparetohandle(buff, sizeof(dongle_info) / sizeof(uint16_t));
        memcpy((void *) buff->ushortbuffer, (void *) &dongle_info, sizeof(dongle_info));
        pool_get_unlock(&obj->workpool, 0, buff);
    }

    obj->state = STAGE_INITIALIZED;
    pthread_mutex_unlock(&obj->state_locker);

    struct sockaddr_in local;
    memset(&local,0,sizeof(local));

    local.sin_family = AF_INET;
    local.sin_port = htons(port);
    local.sin_addr.s_addr = inet_addr(address);

    if (obj->state == STAGE_INITIALIZED) obj->listen_socket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);

    pthread_mutex_lock(&obj->state_locker);
    if (obj->listen_socket != -1) obj->state = STAGE_SOCKET_OPEN;
    pthread_mutex_unlock(&obj->state_locker);

    int r = 1;
    int success = 0;

    if (obj->state == STAGE_SOCKET_OPEN) {
        setsockopt(obj->listen_socket, SOL_SOCKET, SO_REUSEADDR, (char *) &r, sizeof(int));
        struct linger ling = {1, 0};
        setsockopt(obj->listen_socket, SOL_SOCKET, SO_LINGER, (char *) &ling, sizeof(ling));
        if (bind(obj->listen_socket, (struct sockaddr *) &local, sizeof(local)) == 0)  {
            r = fcntl(obj->listen_socket, F_GETFL, 0);
            r = fcntl(obj->listen_socket, F_SETFL, r | O_NONBLOCK);

            if (listen(obj->listen_socket, 1) == 0) {
                LOGI("SdrTcp: Listening on %s:%d", address, port);
                success = 1;
            }
        }
    }

    if (obj->state == STAGE_SOCKET_OPEN && success) RETURN_SUCCESS else {
        LOGI("SdrTcp: Closing sdrtcp due to sdrtcp_open_socket seeing state %d and success %d", obj->state, success);
        RETURN_FAILURE;
    }
}

void sdrtcp_serve_client_async(sdrtcp_t * obj, void * ctx, sdrtcp_command_callback commandcb, sdrtcp_closed_callback closedcb) {
    if (obj->state != STAGE_SOCKET_OPEN) {
        LOGI("SdrTcp: Wrong state when calling sdrtcp_serve_client_async. State is %d", obj->state);
        closedcb(obj, ctx);
        RETURN_AND_CLOSE;
    }

    // async start is imminent
    obj->state = STAGE_CLIENT_OPEN_STARTED_ASYNC;

    pthread_t worker_thread;

    obj->closedcb = closedcb;
    obj->commandcb = commandcb;
    obj->ctx = ctx;

    pthread_attr_t attrs;
    pthread_attr_init(&attrs);
    pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_JOINABLE);
    pthread_create(&worker_thread, &attrs, (void *) tcp_server, (void *) obj);
}

void sdrtcp_stop_serving_client(sdrtcp_t * obj) {

    if (obj->state == STAGE_UNINITIALIZED) {
        LOGI("SdrTcp: Requested sdrtcp stop but already stopped");
        return;
    }

    if (obj->state < STAGE_CLIENT_OPEN_STARTED_ASYNC) {
        LOGI("SdrTcp: Requested sdrtcp stop and stopping now");
        sdrtcp_cleanup(obj);
    } else {
        LOGI("SdrTcp: Requested sdrtcp stop asynchroneously");
        obj->state = STAGE_NEEDS_STOPPING;
    }
}

// queue up data to send over the connection
int sdrtcp_feed(sdrtcp_t * obj, unsigned char  * buf, uint32_t len) {
    extbuffer_t * buff = NULL;
    int succesful = 0;

    if (obj->state == STAGE_CLIENT_SERVING) {
        pthread_mutex_lock(&obj->state_locker);
        if (obj->state == STAGE_CLIENT_SERVING) {
            if ((buff = pool_get_wait_lock(&obj->workpool, 0, 1)) != NULL) {
                extbuffer_preparetohandle(buff, len);
                memcpy((void *) buff->ushortbuffer, (void *) buf,
                       sizeof(uint16_t) * len);
                pool_get_unlock(&obj->workpool, 0, buff);
            }
            succesful = 1;
        } else if (obj->state == STAGE_SOCKET_OPEN || obj->state == STAGE_CLIENT_OPEN || obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {
            usleep(FEED_SLEEP_IF_NOT_READY_MILLIS * 1000);
            succesful = 2; // no client to send data to
        }
        pthread_mutex_unlock(&obj->state_locker);
    } else if (obj->state == STAGE_SOCKET_OPEN || obj->state == STAGE_CLIENT_OPEN || obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {
        usleep(FEED_SLEEP_IF_NOT_READY_MILLIS * 1000);
        succesful = 2; // no client to send data to
    }

    return succesful;
}

void sdrtcp_init(sdrtcp_t * obj) {
    obj->state = 0;
    pthread_mutex_init(&obj->state_locker, NULL);
    obj->client_socket = -1;
    obj->listen_socket = -1;
}

void sdrtcp_free(sdrtcp_t * obj) {
    pthread_mutex_destroy(&obj->state_locker);
}