package com.github.catvod.net;

import android.text.TextUtils;

import com.github.catvod.bean.Proxy;
import com.github.catvod.utils.Util;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class OkProxySelector extends ProxySelector {

    private final List<Proxy> proxy;
    private final ProxySelector system;
    private java.net.Proxy userProxy;
    private boolean authSet;

    public OkProxySelector() {
        proxy = new CopyOnWriteArrayList<>();
        system = ProxySelector.getDefault();
        Authenticator.setDefault(new ProxyAuthenticator(this));
    }

    public synchronized void addAll(List<Proxy> items) {
        if (items.isEmpty()) return;
        items.forEach(Proxy::init);
        proxy.addAll(items);
        proxy.sort(null);
    }

    public synchronized void clear() {
        Authenticator.setDefault(null);
        proxy.clear();
    }

    public synchronized void setProxy(String proxy) {
        this.userProxy = TextUtils.isEmpty(proxy) ? null : parse(proxy);
    }

    private java.net.Proxy parse(String proxy) {
        android.net.Uri uri = android.net.Uri.parse(proxy);
        String userInfo = uri.getUserInfo();
        if (!TextUtils.isEmpty(userInfo) && userInfo.contains(":")) setAuthenticator(userInfo);
        if (uri.getScheme() == null || uri.getHost() == null || uri.getPort() <= 0) return java.net.Proxy.NO_PROXY;
        if (uri.getScheme().startsWith("http")) return new java.net.Proxy(java.net.Proxy.Type.HTTP, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        if (uri.getScheme().startsWith("socks")) return new java.net.Proxy(java.net.Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(uri.getHost(), uri.getPort()));
        return java.net.Proxy.NO_PROXY;
    }

    private void setAuthenticator(String userInfo) {
        String[] auth = userInfo.split(":");
        Authenticator.setDefault(new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(auth[0], auth[1].toCharArray());
            }
        });
    }

    public List<Proxy> getProxy() {
        return proxy;
    }

    private List<java.net.Proxy> fallback(URI uri) {
        return system != null ? system.select(uri) : List.of(java.net.Proxy.NO_PROXY);
    }

    @Override
    public List<java.net.Proxy> select(URI uri) {
        if (userProxy != null && uri.getHost() != null && !"127.0.0.1".equals(uri.getHost()) && !"localhost".equals(uri.getHost())) return List.of(userProxy);
        if (proxy.isEmpty() || uri.getHost() == null || "127.0.0.1".equals(uri.getHost())) return fallback(uri);
        for (Proxy item : proxy) for (String host : item.getHosts()) if (Util.containOrMatch(uri.getHost(), host)) return !item.getProxies().isEmpty() ? item.getProxies() : fallback(uri);
        return fallback(uri);
    }

    @Override
    public void connectFailed(URI uri, SocketAddress socketAddress, IOException e) {
        if (system != null) system.connectFailed(uri, socketAddress, e);
    }
}
