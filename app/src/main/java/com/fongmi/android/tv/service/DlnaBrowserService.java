package com.fongmi.android.tv.service;

import com.fongmi.android.tv.dlna.DLNAServiceConfiguration;

import org.jupnp.UpnpServiceConfiguration;
import org.jupnp.android.AndroidRouter;
import org.jupnp.android.AndroidUpnpServiceImpl;
import org.jupnp.model.types.ServiceType;
import org.jupnp.model.types.UDAServiceType;

public class DlnaBrowserService extends AndroidUpnpServiceImpl {

    private boolean upnpStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        // jUPnP 3.0.4 AndroidUpnpServiceImpl only constructs UpnpServiceImpl and never calls
        // startup(). Binding first leaves getRouter() null; registry/control point still look
        // usable, so clients proceed and later onDestroy() crashes. Always start here.
        try {
            com.fongmi.android.tv.dlna.DlnaMulticastLock.acquire(this);
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
            com.fongmi.android.tv.dlna.DlnaMulticastLock.release();
            return;
        }
        try {
            // Parent does: ((AndroidRouter) getRouter()).unregisterBroadcastReceiver(); then
            // super.shutdown(true). getRouter() is null when startup() never completed.
            super.onDestroy();
        } catch (NullPointerException ignored) {
            shutdownPartiallyInitializedService();
        } finally {
            com.fongmi.android.tv.dlna.DlnaMulticastLock.release();
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
                return new ServiceType[]{new UDAServiceType("ContentDirectory", 1)};
            }
        };
    }
}
