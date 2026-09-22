package com.fongmi.android.tv.service;

import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;
import com.fongmi.android.tv.dlna.DlnaMulticastLock;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidRouter;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DLNACastService extends AndroidUpnpServiceImpl {

    private boolean upnpStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            DlnaMulticastLock.acquire(this, this);
            upnpService.startup();
            upnpStarted = true;
        } catch (RuntimeException ignored) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (!upnpStarted) {
            shutdownPartiallyInitializedService();
            DlnaMulticastLock.release(this);
            return;
        }
        try {
            super.onDestroy();
        } catch (NullPointerException ignored) {
            shutdownPartiallyInitializedService();
        } finally {
            DlnaMulticastLock.release(this);
        }
    }

    private void shutdownPartiallyInitializedService() {
        if (upnpService == null) return;
        try {
            if (upnpService.getRouter() instanceof AndroidRouter router) router.unregisterBroadcastReceiver();
        } catch (RuntimeException ignored) {
        }
        try {
            if (upnpService.getRegistry() != null) upnpService.getRegistry().shutdown();
        } catch (RuntimeException ignored) {
        }
        try {
            if (upnpService.getConfiguration() != null) upnpService.getConfiguration().shutdown();
        } catch (RuntimeException ignored) {
        }
        try {
            if (upnpService.getRouter() != null) upnpService.getRouter().shutdown();
        } catch (Exception ignored) {
        }
    }

    @Override
    protected UpnpServiceConfiguration createConfiguration() {
        return new DLNAServiceConfiguration() {
            @Override
            public ServiceType[] getExclusiveServiceTypes() {
                return new ServiceType[]{new UDAServiceType("AVTransport", 1)};
            }
        };
    }
}
