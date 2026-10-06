package com.github.catvod.net;

import com.github.catvod.utils.Prefers;

import java.net.IDN;
import java.util.Locale;

/** An empty preferred domain disables optional Cloudflare routing. */
public final class CloudflarePreferredSettings {
    private static final String KEY_DOMAIN = "cloudflare_preferred_domain";

    private CloudflarePreferredSettings() {
    }

    public static String getDomain() {
        return Prefers.getString(KEY_DOMAIN, "");
    }

    public static void setDomain(String value) {
        String domain = normalize(value);
        if (domain.equals(getDomain())) return;
        Prefers.put(KEY_DOMAIN, domain);
        OkHttp.echConfigurationChanged();
    }

    /** Accept a hostname only, never a URL, address literal, port or credentials. */
    public static String normalize(String value) {
        if (value == null || value.trim().isEmpty()) return "";
        String host = value.trim();
        if (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.indexOf(':') >= 0 || host.indexOf('/') >= 0 || host.indexOf('\\') >= 0
                || host.indexOf('@') >= 0 || host.indexOf('?') >= 0 || host.indexOf('#') >= 0)
            throw new IllegalArgumentException("Enter a hostname without a URL or port");
        host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        if (host.length() > 253 || host.indexOf('.') < 0 || host.matches("[0-9.]+"))
            throw new IllegalArgumentException("A domain name is required");
        for (String label : host.split("\\.", -1))
            if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
                throw new IllegalArgumentException("Invalid domain name");
        return host;
    }
}
