package com.fongmi.android.tv.update;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ordered download routes; the original GitHub URL is always the final fallback. */
public final class UpdateSources {

    private static final String RELEASES = "https://github.com/wobuhui666/TV/releases/";
    private static final String POLICY = "https://raw.githubusercontent.com/wobuhui666/TV/ui/apple-tv-redesign/ota/policy.json";

    private UpdateSources() {
    }

    public static List<String> manifests(String mode) {
        if (!("leanback".equals(mode) || "mobile".equals(mode))) throw new IllegalArgumentException("Unsupported mode");
        return mirrors(RELEASES + "latest/download/" + mode + ".json");
    }

    public static List<String> policies() {
        return mirrors(POLICY);
    }

    public static List<String> mirrors(String githubUrl) {
        validateSource(githubUrl);
        Set<String> sources = new LinkedHashSet<>();
        sources.add("https://gh-proxy.com/" + githubUrl);
        sources.add("https://ghfast.top/" + githubUrl);
        sources.add(githubUrl);
        return new ArrayList<>(sources);
    }

    private static void validateSource(String value) {
        if (value == null) throw new IllegalArgumentException("Missing GitHub URL");
        try {
            URI uri = new URI(value);
            if ((!value.startsWith(RELEASES) && !value.equals(POLICY)) ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getRawUserInfo() != null ||
                    uri.getPort() != -1 || !uri.normalize().equals(uri) || uri.getRawPath().contains("%")) {
                throw new IllegalArgumentException("Unsupported GitHub update URL");
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid GitHub update URL", e);
        }
    }
}
