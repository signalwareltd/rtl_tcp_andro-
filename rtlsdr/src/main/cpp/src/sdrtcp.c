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

#include <pthread.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <fcntl.h>
#include <time.h>
#include <string.h>
#include <errno.h>


#include "sdrtcp.h"
#include "common.h"
#include "extbuffer.h"

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

#define WAKEUP_PIPE_READ (0)
#define WAKEUP_PIPE_WRITE (1)

/* Self-pipes wake the select() loops immediately on stop. Both ends are
 * non-blocking; a full write end only means a wakeup is already pending. */
static void sdrtcp_pipe_close(int * pipe_fds) {
    if (pipe_fds[WAKEUP_PIPE_READ] != -1) close(pipe_fds[WAKEUP_PIPE_READ]);
    if (pipe_fds[WAKEUP_PIPE_WRITE] != -1) close(pipe_fds[WAKEUP_PIPE_WRITE]);
    pipe_fds[WAKEUP_PIPE_READ] = -1;
    pipe_fds[WAKEUP_PIPE_WRITE] = -1;
}

static void sdrtcp_pipe_open(int * pipe_fds) {
    int fds[2];
    if (pipe(fds) != 0) {
        pipe_fds[WAKEUP_PIPE_READ] = -1;
        pipe_fds[WAKEUP_PIPE_WRITE] = -1;
        return;
    }

    for (int i = 0; i < 2; ++i) {
        int flags = fcntl(fds[i], F_GETFL, 0);
        if (flags != -1) fcntl(fds[i], F_SETFL, flags | O_NONBLOCK);
    }

    pipe_fds[WAKEUP_PIPE_READ] = fds[0];
    pipe_fds[WAKEUP_PIPE_WRITE] = fds[1];
}

static void sdrtcp_pipe_wakeup(int * pipe_fds) {
    if (pipe_fds[WAKEUP_PIPE_WRITE] != -1) {
        const char byte = 0;
        (void) write(pipe_fds[WAKEUP_PIPE_WRITE], &byte, 1);
    }
}

static void sdrtcp_pipe_drain(int * pipe_fds) {
    char buffer[16];
    if (pipe_fds[WAKEUP_PIPE_READ] == -1) return;
    while (read(pipe_fds[WAKEUP_PIPE_READ], buffer, sizeof(buffer)) > 0) {}
}

static int sdrtcp_pipe_add(int * pipe_fds, fd_set * set) {
    if (pipe_fds[WAKEUP_PIPE_READ] == -1) return -1;
    FD_SET(pipe_fds[WAKEUP_PIPE_READ], set);
    return pipe_fds[WAKEUP_PIPE_READ];
}

static int sdrtcp_pipe_isset(int * pipe_fds, fd_set * set) {
    return pipe_fds[WAKEUP_PIPE_READ] != -1 && FD_ISSET(pipe_fds[WAKEUP_PIPE_READ], set);
}

typedef struct {
    char magic[4];
    uint32_t dongleType;
    uint32_t gainsCount;
} dongle_info_t;

static void sdrtcp_cleanup(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state != STAGE_UNINITIALIZED) {
        LOGI("SdrTcp: Closing from state %d", obj->state);
        obj->state = STAGE_UNINITIALIZED;

        pool_free(&obj->workpool);

        if (obj->listen_socket != -1) {
            close(obj->listen_socket);
        }

        if (obj->client_socket != -1) {
            /* Leaving the accepted socket open keeps the peer in CLOSE_WAIT and
             * can eventually wedge the listener (listen backlog is 1). */
            shutdown(obj->client_socket, SHUT_RDWR);
            close(obj->client_socket);
        }

        sdrtcp_pipe_close(obj->server_wakeup);
        sdrtcp_pipe_close(obj->command_wakeup);

        obj->client_socket = -1;
        obj->listen_socket = -1;
    }
    pthread_mutex_unlock(&obj->state_locker);
}

/* Marks the session as stopping and wakes everything that could be waiting on
 * it: the pool wait in serveClient, the server loop and the command loop. */
static void sdrtcp_request_stop(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state != STAGE_UNINITIALIZED) {
        obj->state = STAGE_NEEDS_STOPPING;
    }
    pool_signal_all(&obj->workpool);
    sdrtcp_pipe_wakeup(obj->server_wakeup);
    sdrtcp_pipe_wakeup(obj->command_wakeup);
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
            int maxfd = obj->client_socket;
            int pipefd = sdrtcp_pipe_add(obj->command_wakeup, &readfds);
            if (pipefd > maxfd) maxfd = pipefd;
            tv.tv_sec = 1;
            tv.tv_usec = 0;

            int r = select(maxfd + 1, &readfds, NULL, NULL, &tv);
            if (r < 0) {
                if (errno != EINTR) {
                    LOGI("SdrTcp: commandListener select failed. Code %d", errno);
                    sdrtcp_request_stop(obj);
                    break;
                }
                continue;
            }

            if (sdrtcp_pipe_isset(obj->command_wakeup, &readfds)) {
                sdrtcp_pipe_drain(obj->command_wakeup);
            }

            if (obj->state != STAGE_CLIENT_SERVING) {
                break;
            }

            if (r > 0 && FD_ISSET(obj->client_socket, &readfds)) {
                ssize_t received = recv(obj->client_socket, (char *) &cmd + (sizeof(cmd) - left), left, 0);

                if (received == 0) {
                    /* The client closed its end of the connection. */
                    LOGI("SdrTcp: commandListener: client closed the connection");
                    sdrtcp_request_stop(obj);
                    break;
                } else if (received < 0) {
                    if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) continue;
                    LOGI("SdrTcp: commandListener failed to receive command");
                    sdrtcp_request_stop(obj);
                    break;
                }

                left -= (size_t) received;
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
    fd_set readfds, writefds;

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
            FD_ZERO(&readfds);
            FD_ZERO(&writefds);
            FD_SET(obj->client_socket, &writefds);
            int maxfd = obj->client_socket;
            int pipefd = sdrtcp_pipe_add(obj->server_wakeup, &readfds);
            if (pipefd > maxfd) maxfd = pipefd;
            tv.tv_sec = 5;
            tv.tv_usec = 0;

            int r = select(maxfd + 1, &readfds, &writefds, NULL, &tv);
            if (r < 0) {
                if (errno != EINTR) {
                    int err = errno;
                    char *str_error = strerror(errno);
                    LOGI("SdrTcp: serveClient cannot select. Code %d, exception %s", err,
                         str_error);
                    sdrtcp_request_stop(obj);
                }
                continue;
            }

            if (sdrtcp_pipe_isset(obj->server_wakeup, &readfds)) {
                sdrtcp_pipe_drain(obj->server_wakeup);
            }

            if (obj->state != STAGE_CLIENT_SERVING) {
                break;
            }

            if (r > 0 && FD_ISSET(obj->client_socket, &writefds)) {
                bytessent = send(obj->client_socket, data_to_send + index, bytesleft, 0);

                if (bytessent < 0) {
                    if (errno == EINTR || errno == EAGAIN || errno == EWOULDBLOCK) continue;
                    int err = errno;
                    char *str_error = strerror(errno);
                    LOGI("SdrTcp: serveClient cannot send to client. Code %d, exception %s",
                         err, str_error);
                    sdrtcp_request_stop(obj);
                } else {
                    bytesleft -= (size_t) bytessent;
                    index += (size_t) bytessent;
                }
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
        int maxfd = obj->listen_socket;
        int pipefd = sdrtcp_pipe_add(obj->server_wakeup, &readfds);
        if (pipefd > maxfd) maxfd = pipefd;
        tv.tv_sec = 1;
        tv.tv_usec = 0;

        int r = select(maxfd + 1, &readfds, NULL, NULL, &tv);
        if (r < 0) {
            if (errno == EINTR) continue;
            LOGI("SdrTcp: wait_for_client select failed. Code %d", errno);
            return;
        }

        if (sdrtcp_pipe_isset(obj->server_wakeup, &readfds)) {
            sdrtcp_pipe_drain(obj->server_wakeup);
        }

        if (r > 0 && obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC &&
            FD_ISSET(obj->listen_socket, &readfds)) {
            socklen_t rlen = sizeof(remote);
            obj->client_socket = accept(obj->listen_socket, (struct sockaddr *) &remote, &rlen);
            if (obj->client_socket != -1) {
                /* Non-blocking so a peer that stops reading can never block
                 * send()/recv() in the server or command threads. */
                int flags = fcntl(obj->client_socket, F_GETFL, 0);
                if (flags != -1) fcntl(obj->client_socket, F_SETFL, flags | O_NONBLOCK);

                /* A stop may have been requested while accept() was running.
                 * Do not resurrect the session in that case: close the freshly
                 * accepted client and let tcp_server shut down. */
                pthread_mutex_lock(&obj->state_locker);
                if (obj->state == STAGE_CLIENT_OPEN_STARTED_ASYNC) {
                    obj->state = STAGE_CLIENT_OPEN;
                } else {
                    shutdown(obj->client_socket, SHUT_RDWR);
                    close(obj->client_socket);
                    obj->client_socket = -1;
                }
                pthread_mutex_unlock(&obj->state_locker);
            } else if (errno != EINTR && errno != EAGAIN && errno != EWOULDBLOCK) {
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

    int serving = 0;
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state == STAGE_CLIENT_OPEN) {
        /* The state must be set before the command listener is created: the
         * listener exits immediately if it observes any other state. */
        obj->state = STAGE_CLIENT_SERVING;
        serving = 1;
    }
    pthread_mutex_unlock(&obj->state_locker);

    if (serving) {
        LOGI("SdrTcp: TCP server succesfully started and listening for clients!");
        pthread_t commandThread;
        int command_thread_started = 0;

        pthread_attr_t attrs;
        pthread_attr_init(&attrs);
        pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_JOINABLE);
        int create_result = pthread_create(&commandThread, &attrs, (void *) commandListener, (void *) obj);
        pthread_attr_destroy(&attrs);

        if (create_result == 0) {
            command_thread_started = 1;
        } else {
            LOGI("SdrTcp: Failed to start command listener. Code %d", create_result);
            sdrtcp_request_stop(obj);
        }

        serveClient(obj);

        if (command_thread_started) {
            LOGI("SdrTcp: Waiting for command thread to die");
            void *status;
            pthread_join(commandThread, &status);
        }
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

    /* Everything this thread owns is now released. Let sdrtcp_wait_closed()/
     * sdrtcp_free() know that it is safe to free the session. */
    pthread_mutex_lock(&obj->state_locker);
    obj->worker_thread_done = 1;
    pthread_cond_broadcast(&obj->worker_finished);
    pthread_mutex_unlock(&obj->state_locker);

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

    /* A brand new session does not have a server thread yet. */
    obj->worker_thread_valid = 0;
    obj->worker_thread_done = 1;
    sdrtcp_pipe_close(obj->server_wakeup);
    sdrtcp_pipe_close(obj->command_wakeup);
    sdrtcp_pipe_open(obj->server_wakeup);
    sdrtcp_pipe_open(obj->command_wakeup);
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

int sdrtcp_serve_client_async(sdrtcp_t * obj, void * ctx, sdrtcp_command_callback commandcb, sdrtcp_closed_callback closedcb) {
    if (obj->state != STAGE_SOCKET_OPEN) {
        LOGI("SdrTcp: Wrong state when calling sdrtcp_serve_client_async. State is %d", obj->state);
        closedcb(obj, ctx);
        sdrtcp_cleanup(obj);
        return 0;
    }

    obj->closedcb = closedcb;
    obj->commandcb = commandcb;
    obj->ctx = ctx;

    pthread_attr_t attrs;
    pthread_attr_init(&attrs);
    pthread_attr_setdetachstate(&attrs, PTHREAD_CREATE_JOINABLE);

    pthread_mutex_lock(&obj->state_locker);
    // async start is imminent
    obj->state = STAGE_CLIENT_OPEN_STARTED_ASYNC;
    obj->worker_thread_valid = 0;
    obj->worker_thread_done = 0;

    int create_result = pthread_create(&obj->worker_thread, &attrs, (void *) tcp_server, (void *) obj);
    if (create_result == 0) {
        obj->worker_thread_valid = 1;
    } else {
        obj->worker_thread_done = 1;
    }
    pthread_mutex_unlock(&obj->state_locker);

    pthread_attr_destroy(&attrs);

    if (create_result != 0) {
        LOGI("SdrTcp: Failed to create server thread. Code %d", create_result);
        sdrtcp_cleanup(obj);
        closedcb(obj, ctx);
        return 0;
    }

    return 1;
}

void sdrtcp_stop_serving_client(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);

    if (obj->state == STAGE_UNINITIALIZED) {
        pthread_mutex_unlock(&obj->state_locker);
        LOGI("SdrTcp: Requested sdrtcp stop but already stopped");
        return;
    }

    if (!obj->worker_thread_valid) {
        /* No server thread owns the sockets yet, so it is safe to clean up
         * right here. */
        pthread_mutex_unlock(&obj->state_locker);
        LOGI("SdrTcp: Requested sdrtcp stop and stopping now");
        sdrtcp_cleanup(obj);
    } else {
        /* The server thread owns the sockets and the work pool. It will clean
         * everything up and call closedcb before sdrtcp_wait_closed returns. */
        obj->state = STAGE_NEEDS_STOPPING;
        /* Wake the loops instead of waiting for their select()/pool timeouts. */
        pool_signal_all(&obj->workpool);
        sdrtcp_pipe_wakeup(obj->server_wakeup);
        sdrtcp_pipe_wakeup(obj->command_wakeup);
        pthread_mutex_unlock(&obj->state_locker);
        LOGI("SdrTcp: Requested sdrtcp stop asynchroneously");
    }
}

void sdrtcp_wait_closed(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);
    while (obj->worker_thread_valid && !obj->worker_thread_done) {
        pthread_cond_wait(&obj->worker_finished, &obj->state_locker);
    }
    pthread_mutex_unlock(&obj->state_locker);
}

int sdrtcp_wait_closed_timeout(sdrtcp_t * obj, int timeout_ms) {
    if (timeout_ms < 0) {
        sdrtcp_wait_closed(obj);
        return 1;
    }

    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += timeout_ms / 1000;
    deadline.tv_nsec += (long)(timeout_ms % 1000) * 1000000L;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_sec += 1;
        deadline.tv_nsec -= 1000000000L;
    }

    pthread_mutex_lock(&obj->state_locker);
    while (obj->worker_thread_valid && !obj->worker_thread_done) {
        if (pthread_cond_timedwait(&obj->worker_finished, &obj->state_locker, &deadline) == ETIMEDOUT) {
            break;
        }
    }
    int done = !obj->worker_thread_valid || obj->worker_thread_done;
    pthread_mutex_unlock(&obj->state_locker);
    return done;
}

void sdrtcp_force_close_sockets(sdrtcp_t * obj) {
    pthread_mutex_lock(&obj->state_locker);

    if (obj->state != STAGE_UNINITIALIZED) {
        obj->state = STAGE_NEEDS_STOPPING;
    }

    /* shutdown() wakes select()/recv()/send() in the worker without closing
     * the fds; the worker still owns them and closes them in cleanup. */
    if (obj->client_socket != -1) shutdown(obj->client_socket, SHUT_RDWR);
    if (obj->listen_socket != -1) shutdown(obj->listen_socket, SHUT_RDWR);
    pool_signal_all(&obj->workpool);
    sdrtcp_pipe_wakeup(obj->server_wakeup);
    sdrtcp_pipe_wakeup(obj->command_wakeup);

    pthread_mutex_unlock(&obj->state_locker);
}

// queue up data to send over the connection
int sdrtcp_feed(sdrtcp_t * obj, unsigned char  * buf, uint32_t len) {
    extbuffer_t * buff = NULL;
    int succesful = 0;

    /* Do not sleep or block here: this runs on the driver's receive thread
     * (inside the libusb callback), so waiting while the client is slow or
     * absent delays disconnect handling and every other libusb event. With
     * block=0 a full pool simply drops the buffer (up to 15 x 1 s of delay
     * per unplug was observed with block=1 and a stalled client). */
    pthread_mutex_lock(&obj->state_locker);
    if (obj->state == STAGE_CLIENT_SERVING) {
        if ((buff = pool_get_wait_lock(&obj->workpool, 0, 0)) != NULL) {
            extbuffer_preparetohandle(buff, len);
            memcpy((void *) buff->ushortbuffer, (void *) buf,
                   sizeof(uint16_t) * len);
            pool_get_unlock(&obj->workpool, 0, buff);
        }
        succesful = 1;
    }
    pthread_mutex_unlock(&obj->state_locker);

    return succesful;
}

void sdrtcp_init(sdrtcp_t * obj) {
    obj->state = STAGE_UNINITIALIZED;
    pthread_mutex_init(&obj->state_locker, NULL);
    pthread_cond_init(&obj->worker_finished, NULL);
    obj->client_socket = -1;
    obj->listen_socket = -1;
    obj->server_wakeup[WAKEUP_PIPE_READ] = -1;
    obj->server_wakeup[WAKEUP_PIPE_WRITE] = -1;
    obj->command_wakeup[WAKEUP_PIPE_READ] = -1;
    obj->command_wakeup[WAKEUP_PIPE_WRITE] = -1;
    obj->worker_thread_valid = 0;
    obj->worker_thread_done = 1;
}

void sdrtcp_free(sdrtcp_t * obj) {
    /* Never destroy the lock or the condition variable while the server
     * thread (or something called from it) may still be using them. */
    sdrtcp_wait_closed(obj);
    pthread_cond_destroy(&obj->worker_finished);
    pthread_mutex_destroy(&obj->state_locker);
}