package com.fongmi.android.tv.dlna;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Wi-Fi filters multicast unless a MulticastLock is held. AirPlay/NSD acquires one;
 * jUPnP SSDP must do the same or MediaRenderer discovery silently fails on WLAN.
 */
public final class DlnaMulticastLock {

    private static final String TAG = "DlnaMulticastLock";
    private static final Set<Object> owners = Collections.newSetFromMap(new IdentityHashMap<>());
    private static WifiManager.MulticastLock lock;

    private DlnaMulticastLock() {
    }

    /**
     * Acquire multicast reception for one service instance. Identity-based owners avoid
     * double-counting repeated session starts while allowing the browser and renderer to
     * coexist. Android's MulticastLock reference counting cannot model independent services
     * when they share one static lock, so ownership is tracked here explicitly.
     */
    public static synchronized void acquire(Context context, Object owner) {
        if (owner == null || owners.contains(owner)) return;
        owners.add(owner);
        try {
            if (lock != null && lock.isHeld()) return;
            WifiManager wifi = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi == null) {
                owners.remove(owner);
                return;
            }
            lock = wifi.createMulticastLock("dlna_ssdp");
            lock.setReferenceCounted(false);
            lock.acquire();
            Log.i(TAG, "acquired owners=" + owners.size());
        } catch (RuntimeException e) {
            owners.remove(owner);
            lock = null;
            Log.w(TAG, "acquire failed", e);
        }
    }

    /** Release only this owner; the physical lock stays held for other UPnP services. */
    public static synchronized void release(Object owner) {
        if (owner == null || !owners.remove(owner) || !owners.isEmpty()) return;
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "release failed", e);
        } finally {
            lock = null;
        }
        Log.i(TAG, "released");
    }
}
