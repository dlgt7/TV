package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

/**
 * Wi-Fi filters multicast unless a MulticastLock is held. AirPlay/NSD acquires one;
 * jUPnP SSDP must do the same or MediaRenderer discovery silently fails on WLAN.
 */
public final class DlnaMulticastLock {

    private static final String TAG = "DlnaMulticastLock";
    private static WifiManager.MulticastLock lock;

    private DlnaMulticastLock() {
    }

    public static synchronized void acquire(Context context) {
        try {
            if (lock != null && lock.isHeld()) return;
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) return;
            lock = wifi.createMulticastLock("dlna_ssdp");
            lock.setReferenceCounted(false);
            lock.acquire();
            Log.i(TAG, "acquired");
        } catch (Exception e) {
            Log.w(TAG, "acquire failed", e);
        }
    }

    public static synchronized void release() {
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (Exception e) {
            Log.w(TAG, "release failed", e);
        }
        lock = null;
    }
}
