/*
 * librtlsdr_andro is na addition to the original librtlsdr
 * rtl-sdr, turns your Realtek RTL2832 based DVB dongle into a SDR receiver
 * Copyright (C) 2012 by Steve Markgraf <steve@steve-m.de>
 * Copyright (C) 2012 by Dimitri Stolnikov <horiz0n@gmx.net>
 *
 * Modification 2013 by Martin Marinov <martintzvetomirov@gmail.com>
 * Added: rtlsdr_open2 based on the original rtlsdr_open that accepts file descriptors
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

#include "librtlsdr_andro.h"
#include "rtl-sdr/src/librtlsdr.c"
#include "rtl_tcp_andro.h"

/**
 * If updating to a new librtlsdr and rtlsdr_open in librtlsdr.c has been modified,
 * copy and paste it here and change the line that refers to
 *
 *     r = libusb_open(device, &dev->devh);
 *
 * to
 *
 *     r = libusb_open2(device, &dev->devh, fd);
 *
 * and should be ready to go. This allows communicating with a rtl-sdr dongle that has
 * its file descriptor already open.
 */
int rtlsdr_open2(rtlsdr_dev_t **out_dev, uint32_t index, int fd, const char * uspfs_path_input)
{
	int r;
	int i;
	libusb_device **list;
	rtlsdr_dev_t *dev = NULL;
	libusb_device *device = NULL;
	uint32_t device_count = 0;
	struct libusb_device_descriptor dd;
	uint8_t reg;
	ssize_t cnt;

	dev = malloc(sizeof(rtlsdr_dev_t));
	if (NULL == dev)
		return -ENOMEM;

	memset(dev, 0, sizeof(rtlsdr_dev_t));
	memcpy(dev->fir, fir_default, sizeof(fir_default));

	int status = libusb_init2(&dev->ctx, uspfs_path_input);
	if (status != LIBUSB_SUCCESS)
		return status;
	else if (dev->ctx == NULL)
		return LIBUSB_ERROR_OTHER;

	dev->dev_lost = 1;

	cnt = libusb_get_device_list(dev->ctx, &list);

	for (i = 0; i < cnt; i++) {
		device = list[i];

		libusb_get_device_descriptor(list[i], &dd);

		if (find_known_device(dd.idVendor, dd.idProduct)) {
			device_count++;
		}

		if (index == device_count - 1)
			break;

		device = NULL;
	}

	if (!device) {
		r = -1;
		goto err;
	}

	r = libusb_open2(device, &dev->devh, fd);
	if (r < 0) {
		libusb_free_device_list(list, 1);
		aprintf_stderr("usb_open error %d\n", r);
		if(r == LIBUSB_ERROR_ACCESS)
			aprintf_stderr("Please fix the device permissions, e.g. "
			"by installing the udev rules file rtl-sdr.rules\n");
		goto err;
	}

	libusb_free_device_list(list, 1);

	if (libusb_kernel_driver_active(dev->devh, 0) == 1) {
		dev->driver_active = 1;

#ifdef DETACH_KERNEL_DRIVER
		if (!libusb_detach_kernel_driver(dev->devh, 0)) {
			aprintf_stderr("Detached kernel driver\n");
		} else {
			aprintf_stderr("Detaching kernel driver failed!");
			goto err;
		}
#else
		aprintf_stderr("\nKernel driver is active, or device is "
				"claimed by second instance of librtlsdr."
				"\nIn the first case, please either detach"
				" or blacklist the kernel module\n"
				"(dvb_usb_rtl28xxu), or enable automatic"
				" detaching at compile time.\n\n");
#endif
	}

	r = libusb_claim_interface(dev->devh, 0);
	if (r < 0) {
		aprintf_stderr("usb_claim_interface error %d\n", r);
		goto err;
	}

	dev->rtl_xtal = DEF_RTL_XTAL_FREQ;

	/* perform a dummy write, if it fails, reset the device */
	if (rtlsdr_write_reg(dev, USBB, USB_SYSCTL, 0x09, 1) < 0) {
		aprintf_stderr("Resetting device...\n");
		libusb_reset_device(dev->devh);
	}

	rtlsdr_init_baseband(dev);
	dev->dev_lost = 0;

	/* Probe tuners */
	rtlsdr_set_i2c_repeater(dev, 1);

	reg = rtlsdr_i2c_read_reg(dev, E4K_I2C_ADDR, E4K_CHECK_ADDR);
	if (reg == E4K_CHECK_VAL) {
		aprintf_stderr("Found Elonics E4000 tuner\n");
		dev->tuner_type = RTLSDR_TUNER_E4000;
		goto found;
	}

	reg = rtlsdr_i2c_read_reg(dev, FC0013_I2C_ADDR, FC0013_CHECK_ADDR);
	if (reg == FC0013_CHECK_VAL) {
		aprintf_stderr("Found Fitipower FC0013 tuner\n");
		dev->tuner_type = RTLSDR_TUNER_FC0013;
		goto found;
	}

	reg = rtlsdr_i2c_read_reg(dev, R820T_I2C_ADDR, R82XX_CHECK_ADDR);
	if (reg == R82XX_CHECK_VAL) {
		aprintf_stderr("Found Rafael Micro R820T tuner\n");
		dev->tuner_type = RTLSDR_TUNER_R820T;
		goto found;
	}

	reg = rtlsdr_i2c_read_reg(dev, R828D_I2C_ADDR, R82XX_CHECK_ADDR);
	if (reg == R82XX_CHECK_VAL) {
		aprintf_stderr("Found Rafael Micro R828D tuner\n");
		dev->tuner_type = RTLSDR_TUNER_R828D;
		goto found;
	}

	/* initialise GPIOs */
	rtlsdr_set_gpio_output(dev, 5);

	/* reset tuner before probing */
	rtlsdr_set_gpio_bit(dev, 5, 1);
	rtlsdr_set_gpio_bit(dev, 5, 0);

	reg = rtlsdr_i2c_read_reg(dev, FC2580_I2C_ADDR, FC2580_CHECK_ADDR);
	if ((reg & 0x7f) == FC2580_CHECK_VAL) {
		aprintf_stderr("Found FCI 2580 tuner\n");
		dev->tuner_type = RTLSDR_TUNER_FC2580;
		goto found;
	}

	reg = rtlsdr_i2c_read_reg(dev, FC0012_I2C_ADDR, FC0012_CHECK_ADDR);
	if (reg == FC0012_CHECK_VAL) {
		aprintf_stderr("Found Fitipower FC0012 tuner\n");
		rtlsdr_set_gpio_output(dev, 6);
		dev->tuner_type = RTLSDR_TUNER_FC0012;
		goto found;
	}

found:
	/* use the rtl clock value by default */
	dev->tun_xtal = dev->rtl_xtal;
	dev->tuner = &tuners[dev->tuner_type];

	switch (dev->tuner_type) {
	case RTLSDR_TUNER_R828D:
		dev->tun_xtal = R828D_XTAL_FREQ;
	case RTLSDR_TUNER_R820T:
		/* disable Zero-IF mode */
		rtlsdr_demod_write_reg(dev, 1, 0xb1, 0x1a, 1);

		/* only enable In-phase ADC input */
		rtlsdr_demod_write_reg(dev, 0, 0x08, 0x4d, 1);

		/* the R82XX use 3.57 MHz IF for the DVB-T 6 MHz mode, and
		 * 4.57 MHz for the 8 MHz mode */
		rtlsdr_set_if_freq(dev, R82XX_IF_FREQ);

		/* enable spectrum inversion */
		rtlsdr_demod_write_reg(dev, 1, 0x15, 0x01, 1);
		break;
	case RTLSDR_TUNER_UNKNOWN:
		aprintf_stderr("No supported tuner found\n");
		rtlsdr_set_direct_sampling(dev, 1);
		break;
	default:
		break;
	}

	if (dev->tuner->init)
		r = dev->tuner->init(dev);

	rtlsdr_set_i2c_repeater(dev, 0);

	*out_dev = dev;

	return 0;
err:
	if (dev) {
		if (dev->ctx)
			libusb_exit(dev->ctx);

		free(dev);
	}

	return r;
}
