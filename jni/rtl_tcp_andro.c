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

#include <errno.h>
#include <signal.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>

#include <unistd.h>
#include <arpa/inet.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <sys/time.h>
#include <netinet/in.h>
#include <fcntl.h>

#include <pthread.h>

// For exit codes
#include "RtlTcp.h"

#include "rtl_tcp_andro.h"
#include "librtlsdr_andro.h"
#include "rtl-sdr/src/convenience/convenience.h"

#define closesocket close
#define SOCKADDR struct sockaddr
#define SOCKET int
#define SOCKET_ERROR -1

static SOCKET s;

static pthread_t tcp_worker_thread;
static pthread_t command_thread;

static pthread_mutex_t ll_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_mutex_t running_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t cond = PTHREAD_COND_INITIALIZER;

struct llist {
	char *data;
	size_t len;
	struct llist *next;
};

typedef struct { /* structure size must be multiple of 2 bytes */
	char magic[4];
	uint32_t tuner_type;
	uint32_t tuner_gain_count;
} dongle_info_t;

static rtlsdr_dev_t *dev = NULL;

static int global_numq = 0;
static struct llist *ll_buffers = 0;
static int llbuf_num = 500;

static volatile int do_exit = 0;
static volatile int is_running = 0;

void init_all_variables() {
	dev = NULL;
	global_numq = 0;
	llbuf_num = 500;
	do_exit = 0;

	if (ll_buffers != 0) {
		struct llist *curelem,*prev;
		curelem = ll_buffers;
		ll_buffers = 0;

		while(curelem != 0) {
			prev = curelem;
			curelem = curelem->next;
			free(prev->data);
			free(prev);
		}
	}
}

static void sighandler(int signum)
{

	if (dev != NULL)
		rtlsdr_cancel_async(dev);
	if (signum != 0) {
		aprintf_stderr("Signal caught, exiting! (signal %d)", signum);
		announce_exceptioncode(marto_rtl_tcp_andro_core_RtlTcp_EXIT_SIGNAL_CAUGHT);
	}

	do_exit = 1;
}

void rtlsdr_callback(unsigned char *buf, uint32_t len, void *ctx)
{
	if(!do_exit) {
		struct llist *rpt = (struct llist*)malloc(sizeof(struct llist));
		rpt->data = (char*)malloc(len);
		memcpy(rpt->data, buf, len);
		rpt->len = len;
		rpt->next = NULL;

		pthread_mutex_lock(&ll_mutex);

		if (ll_buffers == NULL) {
			ll_buffers = rpt;
		} else {
			struct llist *cur = ll_buffers;
			int num_queued = 0;

			while (cur->next != NULL) {
				cur = cur->next;
				num_queued++;
			}

			if(llbuf_num && llbuf_num == num_queued-2){
				struct llist *curelem;

				free(ll_buffers->data);
				curelem = ll_buffers->next;
				free(ll_buffers);
				ll_buffers = curelem;
			}

			cur->next = rpt;

			global_numq = num_queued;
		}
		pthread_cond_signal(&cond);
		pthread_mutex_unlock(&ll_mutex);
	}
}

static void tcp_worker(void *arg)
{
	struct llist *curelem,*prev;
	int bytesleft,bytessent, index;
	struct timeval tv= {1,0};
	struct timespec ts;
	struct timeval tp;
	fd_set writefds;
	int r = 0;

	while(1) {
		if(do_exit) {
			thread_detach();
			pthread_exit(0);
		}

		pthread_mutex_lock(&ll_mutex);
		gettimeofday(&tp, NULL);
		ts.tv_sec  = tp.tv_sec+5;
		ts.tv_nsec = tp.tv_usec * 1000;
		r = pthread_cond_timedwait(&cond, &ll_mutex, &ts);
		if(r == ETIMEDOUT) {
			pthread_mutex_unlock(&ll_mutex);
			aprintf("worker cond timeout");
			sighandler(0);
			thread_detach();
			pthread_exit(NULL);
		}

		curelem = ll_buffers;
		ll_buffers = 0;
		pthread_mutex_unlock(&ll_mutex);

		while(curelem != 0) {
			bytesleft = curelem->len;
			index = 0;
			bytessent = 0;
			while(bytesleft > 0) {
				FD_ZERO(&writefds);
				FD_SET(s, &writefds);
				tv.tv_sec = 1;
				tv.tv_usec = 0;
				r = select(s+1, NULL, &writefds, NULL, &tv);
				if(r) {
					bytessent = send(s,  &curelem->data[index], bytesleft, 0);
					bytesleft -= bytessent;
					index += bytessent;
				}
				if(bytessent == SOCKET_ERROR || do_exit) {
						aprintf("worker socket bye");
						sighandler(0);
						thread_detach();
						pthread_exit(NULL);
				}
			}
			prev = curelem;
			curelem = curelem->next;
			free(prev->data);
			free(prev);
		}
	}
	thread_detach();
	pthread_exit(NULL);
}

static int set_gain_by_index(rtlsdr_dev_t *_dev, unsigned int index)
{
	int res = 0;
	int* gains;
	int count = rtlsdr_get_tuner_gains(_dev, NULL);

	if (count > 0 && (unsigned int)count > index) {
		gains = malloc(sizeof(int) * count);
		count = rtlsdr_get_tuner_gains(_dev, gains);

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
        if (index >= (unsigned int) count) index = count - 1;

        gains = malloc(sizeof(int) * count);
        count = rtlsdr_get_tuner_gains(_dev, gains);

        res = rtlsdr_set_tuner_gain(_dev, gains[index]);

        free(gains);

        return res;
}

struct command{
	unsigned char cmd;
	unsigned int param;
}__attribute__((packed));

static void command_worker(void *arg)
{
	int left, received = 0;
	fd_set readfds;
	struct command cmd={0, 0};
	struct timeval tv= {1, 0};
	int r = 0;
	uint32_t tmp;

	while(1) {
		left=sizeof(cmd);
		while(left >0) {
			FD_ZERO(&readfds);
			FD_SET(s, &readfds);
			tv.tv_sec = 1;
			tv.tv_usec = 0;
			r = select(s+1, &readfds, NULL, NULL, &tv);
			if(r) {
				received = recv(s, (char*)&cmd+(sizeof(cmd)-left), left, 0);
				left -= received;
			}
			if(received == SOCKET_ERROR || do_exit) {
				aprintf("comm recv bye");
				sighandler(0);
				thread_detach();
				pthread_exit(NULL);
			}
		}
		switch(cmd.cmd) {
		case 0x01:
			//aprintf("set freq %d", ntohl(cmd.param));
			rtlsdr_set_center_freq(dev,ntohl(cmd.param));
			break;
		case 0x02:
			//aprintf("set sample rate %d", ntohl(cmd.param));
			rtlsdr_set_sample_rate(dev, ntohl(cmd.param));
			break;
		case 0x03:
			//aprintf("set gain mode %d", ntohl(cmd.param));
			rtlsdr_set_tuner_gain_mode(dev, ntohl(cmd.param));
			break;
		case 0x04:
			//aprintf("set gain %d", ntohl(cmd.param));
			rtlsdr_set_tuner_gain(dev, ntohl(cmd.param));
			break;
		case 0x05:
			//aprintf("set freq correction %d", ntohl(cmd.param));
			rtlsdr_set_freq_correction(dev, ntohl(cmd.param));
			break;
		case 0x06:
			tmp = ntohl(cmd.param);
			//aprintf("set if stage %d gain %d", tmp >> 16, (short)(tmp & 0xffff));
			rtlsdr_set_tuner_if_gain(dev, tmp >> 16, (short)(tmp & 0xffff));
			break;
		case 0x07:
			//aprintf("set test mode %d", ntohl(cmd.param));
			rtlsdr_set_testmode(dev, ntohl(cmd.param));
			break;
		case 0x08:
			//aprintf("set agc mode %d", ntohl(cmd.param));
			rtlsdr_set_agc_mode(dev, ntohl(cmd.param));
			break;
		case 0x09:
			//aprintf("set direct sampling %d", ntohl(cmd.param));
			rtlsdr_set_direct_sampling(dev, ntohl(cmd.param));
			break;
		case 0x0a:
			//aprintf("set offset tuning %d", ntohl(cmd.param));
			rtlsdr_set_offset_tuning(dev, ntohl(cmd.param));
			break;
		case 0x0b:
			//aprintf("set rtl xtal %d", ntohl(cmd.param));
			rtlsdr_set_xtal_freq(dev, ntohl(cmd.param), 0);
			break;
		case 0x0c:
			//aprintf("set tuner xtal %d", ntohl(cmd.param));
			rtlsdr_set_xtal_freq(dev, 0, ntohl(cmd.param));
			break;
		case 0x0d:
			//aprintf("set tuner gain by index %d", ntohl(cmd.param));
			set_gain_by_index(dev, ntohl(cmd.param));
			break;
		case 0x7e:
			//aprintf("client requested to close rtl_tcp_andro");
			sighandler(0);
			break;
		case 0x7f:
			set_gain_by_perc(dev, ntohl(cmd.param));
			break;
		default:
			break;
		}
		cmd.cmd = 0xff;
	}
	thread_detach();
	pthread_exit(NULL);
	return;
}

void rtltcp_close() {
	sighandler(0);
}

int rtltcp_isrunning() {
	int isit_running = 0;
	pthread_mutex_lock(&running_mutex);
	isit_running = is_running;
	pthread_mutex_unlock(&running_mutex);
	return isit_running;
}

void rtltcp_main(int usbfd, const char * uspfs_path_input, int argc, char **argv)
{
	int r, opt;
	char* addr = "127.0.0.1";
	int port = 1234;
	uint32_t frequency = 100000000, samp_rate = 2048000;
	struct sockaddr_in local, remote;
	uint32_t buf_num = 0;
	int dev_index = 0;
	int dev_given = 0;
	int gain = 0;
	int ppm_error = 0;
	struct llist *curelem,*prev;
	pthread_attr_t attr;
	void *status;
	struct timeval tv = {1,0};
	struct linger ling = {1,0};
	SOCKET listensocket;
	socklen_t rlen;
	fd_set readfds;
	dongle_info_t dongle_info;


	pthread_mutex_lock(&running_mutex);
	is_running = 1;
	pthread_mutex_unlock(&running_mutex);

	if (usbfd != -1 && (!(fcntl(usbfd, F_GETFL) != -1 || errno != EBADF))) {
		aprintf_stderr("Invalid file descriptor %d, - %s", usbfd, strerror(errno));
		announce_exceptioncode(marto_rtl_tcp_andro_core_RtlTcp_EXIT_INVALID_FD);
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	init_all_variables();

	struct sigaction sigact, sigign;

	while ((opt = getopt(argc, argv, "a:p:f:g:s:b:n:d:P:")) != -1) {
		switch (opt) {
		case 'd':
			dev_index = verbose_device_search(optarg);
			dev_given = 1;
			break;
		case 'f':
			frequency = (uint32_t)atofs(optarg);
			break;
		case 'g':
			gain = (int)(atof(optarg) * 10); /* tenths of a dB */
			break;
		case 's':
			samp_rate = (uint32_t)atofs(optarg);
			break;
		case 'a':
			addr = optarg;
			break;
		case 'p':
			port = atoi(optarg);
			break;
		case 'b':
			buf_num = atoi(optarg);
			break;
		case 'n':
			llbuf_num = atoi(optarg);
			break;
		case 'P':
			ppm_error = atoi(optarg);
			break;
		default:
			aprintf_stderr("Unexpected argument '%c' with value '%s' received as an argument", opt, optarg);
			announce_exceptioncode(marto_rtl_tcp_andro_core_RtlTcp_EXIT_WRONG_ARGS);
			pthread_mutex_lock(&running_mutex);
			is_running = 0;
			pthread_mutex_unlock(&running_mutex);
			return;
		}
	}

	if (argc < optind) {
		aprintf_stderr("Expected at least %d arguments, but got %d", optind, argc);
		announce_exceptioncode(marto_rtl_tcp_andro_core_RtlTcp_EXIT_WRONG_ARGS);
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	optind = 0; // this is important since we will look at arguments more than once

	sigact.sa_handler = sighandler;
	sigemptyset(&sigact.sa_mask);
	sigact.sa_flags = 0;
	sigign.sa_handler = SIG_IGN;
	sigaction(SIGINT, &sigact, NULL);
	sigaction(SIGTERM, &sigact, NULL);
	sigaction(SIGQUIT, &sigact, NULL);
	sigaction(SIGPIPE, &sigign, NULL);

	r = 0;
	if (usbfd == -1) {


		if (!dev_given) {
			dev_index = verbose_device_search("0");
		}

		if (dev_index < 0) {
			aprintf_stderr("No supported devices found.");
			announce_exceptioncode( marto_rtl_tcp_andro_core_RtlTcp_EXIT_NO_DEVICES );
			pthread_mutex_lock(&running_mutex);
			is_running = 0;
			pthread_mutex_unlock(&running_mutex);
			return;
		}

		r = rtlsdr_open(&dev, dev_index);
	} else {
		aprintf("Opening device with fd %d at %s", usbfd, uspfs_path_input);
		r = rtlsdr_open2(&dev, dev_index, usbfd, uspfs_path_input);
	}


	if (r < 0) {
		announce_exceptioncode( r );
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	if (NULL == dev) {
		aprintf_stderr("Failed to open rtlsdr device #%d.", dev_index);
		announce_exceptioncode( marto_rtl_tcp_andro_core_RtlTcp_EXIT_FAILED_TO_OPEN_DEVICE );
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	/* Set the tuner error */
	verbose_ppm_set(dev, ppm_error);

	/* Set the sample rate */
	r = rtlsdr_set_sample_rate(dev, samp_rate);
	if (r < 0) {
		aprintf_stderr("WARNING: Failed to set sample rate.");
		announce_exceptioncode( marto_rtl_tcp_andro_core_RtlTcp_EXIT_NOT_ENOUGH_POWER );
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	/* Set the frequency */
	r = rtlsdr_set_center_freq(dev, frequency);
	if (r < 0)
		aprintf_stderr("WARNING: Failed to set center freq.");
	else
		aprintf_stderr("Tuned to %i Hz.", frequency);

	if (0 == gain) {
		/* Enable automatic gain */
		r = rtlsdr_set_tuner_gain_mode(dev, 0);
		if (r < 0)
			aprintf_stderr("WARNING: Failed to enable automatic gain.");
	} else {
		/* Enable manual gain */
		r = rtlsdr_set_tuner_gain_mode(dev, 1);
		if (r < 0)
			aprintf_stderr("WARNING: Failed to enable manual gain.");

		/* Set the tuner gain */
		r = rtlsdr_set_tuner_gain(dev, gain);
		if (r < 0)
			aprintf_stderr("WARNING: Failed to set tuner gain.");
		else
			aprintf_stderr("Tuner gain set to %f dB.", gain/10.0);
	}

	/* Reset endpoint before we start reading from it (mandatory) */
	r = rtlsdr_reset_buffer(dev);
	if (r < 0) {
		aprintf_stderr("WARNING: Failed to reset buffers.");
		announce_exceptioncode( marto_rtl_tcp_andro_core_RtlTcp_EXIT_FAILED_TO_OPEN_DEVICE );
		pthread_mutex_lock(&running_mutex);
		is_running = 0;
		pthread_mutex_unlock(&running_mutex);
		return;
	}

	memset(&local,0,sizeof(local));
	local.sin_family = AF_INET;
	local.sin_port = htons(port);
	local.sin_addr.s_addr = inet_addr(addr);

	listensocket = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
	r = 1;
	setsockopt(listensocket, SOL_SOCKET, SO_REUSEADDR, (char *)&r, sizeof(int));
	setsockopt(listensocket, SOL_SOCKET, SO_LINGER, (char *)&ling, sizeof(ling));
	bind(listensocket,(struct sockaddr *)&local,sizeof(local));

	r = fcntl(listensocket, F_GETFL, 0);
	r = fcntl(listensocket, F_SETFL, r | O_NONBLOCK);

	announce_success();
	aprintf("listening on %s:%d...", addr, port);
	listen(listensocket,1);

	while(1) {
		FD_ZERO(&readfds);
		FD_SET(listensocket, &readfds);
		tv.tv_sec = 1;
		tv.tv_usec = 0;
		r = select(listensocket+1, &readfds, NULL, NULL, &tv);
		if(do_exit) {
			goto out;
		} else if(r) {
			rlen = sizeof(remote);
			s = accept(listensocket,(struct sockaddr *)&remote, &rlen);
			break;
		}
	}

	setsockopt(s, SOL_SOCKET, SO_LINGER, (char *)&ling, sizeof(ling));

	aprintf("client accepted!");

	memset(&dongle_info, 0, sizeof(dongle_info));
	memcpy(&dongle_info.magic, "RTL0", 4);

	r = rtlsdr_get_tuner_type(dev);
	if (r >= 0)
		dongle_info.tuner_type = htonl(r);

	r = rtlsdr_get_tuner_gains(dev, NULL);
	if (r >= 0)
		dongle_info.tuner_gain_count = htonl(r);

	r = send(s, (const char *)&dongle_info, sizeof(dongle_info), 0);
	if (sizeof(dongle_info) != r)
		aprintf("failed to send dongle information");

	pthread_attr_init(&attr);
	pthread_attr_setdetachstate(&attr, PTHREAD_CREATE_JOINABLE);
	r = pthread_create(&tcp_worker_thread, &attr, (void *) tcp_worker, NULL);
	r = pthread_create(&command_thread, &attr, (void *) command_worker, NULL);
	pthread_attr_destroy(&attr);

	r = rtlsdr_read_async(dev, rtlsdr_callback, NULL, buf_num, 0);

	pthread_join(tcp_worker_thread, &status);
	pthread_join(command_thread, &status);

	closesocket(s);

	aprintf("all threads dead..");
	curelem = ll_buffers;
	ll_buffers = 0;

	while(curelem != 0) {
		prev = curelem;
		curelem = curelem->next;
		if (prev->data != NULL) free(prev->data);
		free(prev);
	}

	do_exit = 0;
	global_numq = 0;

	out:
	rtlsdr_close(dev);
	closesocket(listensocket);
	closesocket(s);
	announce_exceptioncode(marto_rtl_tcp_andro_core_RtlTcp_EXIT_OK);
	pthread_mutex_lock(&running_mutex);
	is_running = 0;
	pthread_mutex_unlock(&running_mutex);
	aprintf("bye!");
	return;
}
