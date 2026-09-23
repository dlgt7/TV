package com.fongmi.android.tv.dlna;

import android.os.Build;
import android.text.TextUtils;

import com.fongmi.android.tv.setting.DlnaSetting;

import org.jupnp.android.AndroidNetworkAddressFactory;
import org.jupnp.android.AndroidUpnpServiceConfiguration;
import org.jupnp.model.ServerClientTokens;
import org.jupnp.transport.spi.NetworkAddressFactory;
import org.jupnp.transport.spi.StreamClient;
import org.jupnp.transport.spi.StreamServer;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class DLNAServiceConfiguration extends AndroidUpnpServiceConfiguration {

    private final boolean bindPreferredOnly;

    /** jUPnP stream server port; 0 means "any free port" but some controllers ignore ephemeral LOCATIONs. */
    private static final int DEFAULT_STREAM_PORT = 49152;

    public DLNAServiceConfiguration() {
        this(false);
    }

    public DLNAServiceConfiguration(boolean bindPreferredOnly) {
        super(resolveListenPort(bindPreferredOnly), 0);
        this.bindPreferredOnly = bindPreferredOnly;
    }

    private static int resolveListenPort(boolean bindPreferredOnly) {
        if (!bindPreferredOnly) return 0;
        int port = DlnaSetting.getHttpPort();
        return port > 0 ? port : DEFAULT_STREAM_PORT;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StreamClient createStreamClient() {
        return new OkHttpStreamClient(new OkHttpStreamClient.Configuration(getSyncProtocolExecutorService()) {
            @Override
            public String getUserAgentValue(int majorVersion, int minorVersion) {
                ServerClientTokens tokens = new ServerClientTokens(majorVersion, minorVersion);
                tokens.setOsVersion(Build.VERSION.RELEASE);
                tokens.setOsName("Android");
                return tokens.toString();
            }
        });
    }

    @Override
    @SuppressWarnings("rawtypes")
    public StreamServer createStreamServer(NetworkAddressFactory networkAddressFactory) {
        boolean fallbackToEphemeral = bindPreferredOnly && DlnaSetting.getHttpPort() == 0;
        return new SocketHttpStreamServer(new SocketHttpStreamServer.Configuration(networkAddressFactory.getStreamListenPort(), fallbackToEphemeral));
    }

    @Override
    protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort, int multicastResponsePort) {
        // Always bind only the real LAN iface (and IPv4). Hosts like rk3588 + Docker expose
        // docker0/br-*/tailscale0 and dozens of IPv6 temps; binding them multiplies SSDP
        // sockets and delays MediaServer discovery (e.g. slow IPTV boxes).
        String iface = DlnaSetting.resolveInterfaceName();
        return new AndroidNetworkAddressFactory(streamListenPort, multicastResponsePort) {
            @Override
            protected boolean isUsableNetworkInterface(NetworkInterface networkInterface) throws Exception {
                if (!super.isUsableNetworkInterface(networkInterface)) return false;
                if (!DlnaNetwork.isCandidate(networkInterface)) return false;
                return TextUtils.isEmpty(iface) || iface.equals(networkInterface.getName());
            }

            @Override
            protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
                return address instanceof Inet4Address && super.isUsableAddress(networkInterface, address);
            }
        };
    }
}
