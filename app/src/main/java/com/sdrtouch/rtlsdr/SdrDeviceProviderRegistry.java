package com.sdrtouch.rtlsdr;

import com.sdrtouch.core.devices.SdrDeviceProvider;
import com.sdrtouch.rtlsdr.driver.RtlSdrDeviceProvider;
import com.sdrtouch.rtlsdr.hackrf.HackRfDeviceProvider;

public class SdrDeviceProviderRegistry {
    final static SdrDeviceProvider[] SDR_DEVICE_PROVIDERS = new SdrDeviceProvider[] {
            new RtlSdrDeviceProvider(),
            new HackRfDeviceProvider(),
    };
}
