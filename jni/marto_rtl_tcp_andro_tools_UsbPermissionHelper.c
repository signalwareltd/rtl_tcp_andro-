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

#include "marto_rtl_tcp_andro_tools_UsbPermissionHelper.h"
#include <fcntl.h>
#include <errno.h>

#include <sys/socket.h>
#include <pthread.h>

#include <android/log.h>
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, "SDR", __VA_ARGS__))

pthread_t thread;

struct sockaddr_un {
    unsigned short sun_family;
    char sun_path[108];
};

typedef struct fd_path_holder {
	int fd;
	char path[108];
} fd_path_holder_t;

/* size of control buffer to send/recv one file descriptor */
#define CONTROLLEN  CMSG_LEN(sizeof(int))

static struct cmsghdr   *cmptr = NULL;  /* malloc'ed first time */

/*
 * Pass a file descriptor to another process.
 * If fd<0, then -fd is sent back instead as the error status.
 */
int
send_fd(int fd, int fd_to_send)
{
    struct iovec    iov[1];
    struct msghdr   msg;
    char            buf[2]; /* send_fd()/recv_fd() 2-byte protocol */

    iov[0].iov_base = buf;
    iov[0].iov_len  = 2;
    msg.msg_iov     = iov;
    msg.msg_iovlen  = 1;
    msg.msg_name    = NULL;
    msg.msg_namelen = 0;
    if (fd_to_send < 0) {
        msg.msg_control    = NULL;
        msg.msg_controllen = 0;
        buf[1] = -fd_to_send;   /* nonzero status means error */
        if (buf[1] == 0)
            buf[1] = 1; /* -256, etc. would screw up protocol */
    } else {
        if (cmptr == NULL && (cmptr = malloc(CONTROLLEN)) == NULL)
            return(-1);
        cmptr->cmsg_level  = SOL_SOCKET;
        cmptr->cmsg_type   = SCM_RIGHTS;
        cmptr->cmsg_len    = CONTROLLEN;
        msg.msg_control    = cmptr;
        msg.msg_controllen = CONTROLLEN;
        *(int *)CMSG_DATA(cmptr) = fd_to_send;     /* the fd to pass */
        buf[1] = 0;          /* zero status means OK */
    }
    buf[0] = 0;              /* null byte flag to recv_fd() */
    if (sendmsg(fd, &msg, 0) != 2)
        return(-1);
    return(0);
}

void startAsync(void *ctx) {
	fd_path_holder_t * holder = (fd_path_holder_t *) ctx;
	int done, n;
	unsigned int s, s2, t;
	struct sockaddr_un local, remote;
	int len;

	s = socket(AF_UNIX, SOCK_STREAM, 0);

	if (s < 0) {
		LOGI("UNIX Socket returned an error %s", strerror(errno));
		return;
	}

	local.sun_family = AF_UNIX;  /* local is declared before socket() ^ */
	strcpy(local.sun_path, holder->path);

	unlink(local.sun_path);
	len = strlen(local.sun_path) + sizeof(local.sun_family);
	if (bind(s, (struct sockaddr *)&local, len) == -1) {
        LOGI("UNIX Bind error");
        return;
    }

	if (listen(s, 1) == -1) {
		LOGI("UNIX Listen error");
		return;
	}

	t = sizeof(remote);
	if ((s2 = accept(s, (struct sockaddr *)&remote, &t)) == -1) {
		LOGI("UNIX Accept error");
		return;
	}

	send_fd(s2, holder->fd);

	close(s2);

	free(ctx);
}

JNIEXPORT void JNICALL Java_marto_rtl_1tcp_1andro_tools_UsbPermissionHelper_native_1startUnixSocketServer
(JNIEnv * env, jclass class, jstring address, jint fd) {
	const char *path = (*env)->GetStringUTFChars(env, address, 0);
	fd_path_holder_t * ctx = (fd_path_holder_t *) malloc(sizeof(fd_path_holder_t));
	strcpy(ctx->path, path);
	ctx->fd = (int) fd;

	pthread_create (&thread, NULL, (void *) &startAsync, ctx);

	(*env)->ReleaseStringUTFChars(env, address, path);
}
