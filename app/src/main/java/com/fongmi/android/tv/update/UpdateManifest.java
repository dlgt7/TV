package com.fongmi.android.tv.update;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/** Validated metadata for one application variant and one immutable release asset. */
public final class UpdateManifest {

    public static final long MAX_ASSET_BYTES = 1024L * 1024 * 1024;

    public final int code;
    public final String name;
    public final String desc;
    public final Asset asset;

    private UpdateManifest(int code, String name, String desc, Asset asset) {
        this.code = code;
        this.name = name;
        this.desc = desc;
        this.asset = asset;
    }

    public static UpdateManifest parse(String json, String packageName, String mode, String abi) throws IOException {
        if (!("leanback".equals(mode) || "mobile".equals(mode)) ||
                !("arm64_v8a".equals(abi) || "armeabi_v7a".equals(abi)) ||
                packageName == null || packageName.isEmpty()) {
            throw new IOException("Unsupported application variant");
        }
        JSONObject root = parseObject(json);
        requireLong(root, "schema", 1, 1);
        int code = (int) requireLong(root, "code", 1, Integer.MAX_VALUE);
        String name = requireString(root, "name");
        if (name.trim().isEmpty()) throw new IOException("Missing release name");
        String desc = root.has("desc") ? requireString(root, "desc") : "";
        if (!packageName.equals(requireString(root, "packageName")) || !mode.equals(requireString(root, "mode"))) {
            throw new IOException("Release does not match this application");
        }
        JSONObject asset = requireObject(requireObject(root, "assets"), abi);
        String url = requireString(asset, "url");
        validateAssetUrl(url, mode, abi);
        long size = requireLong(asset, "size", 1, MAX_ASSET_BYTES);
        String sha256 = requireString(asset, "sha256");
        if (!sha256.matches("[a-fA-F0-9]{64}")) throw new IOException("Invalid APK checksum");
        return new UpdateManifest(code, name, desc, new Asset(url, size, sha256.toLowerCase(Locale.ROOT)));
    }

    private static void validateAssetUrl(String value, String mode, String abi) throws IOException {
        try {
            URI uri = new URI(value);
            String prefix = "/wobuhui666/TV/releases/download/";
            String path = uri.getRawPath();
            if (!"https".equals(uri.getScheme()) || !"github.com".equals(uri.getHost()) ||
                    uri.getRawUserInfo() != null || uri.getPort() != -1 ||
                    uri.getRawQuery() != null || uri.getRawFragment() != null ||
                    path == null || !path.startsWith(prefix)) {
                throw new IOException("APK must use this project's GitHub release");
            }
            String[] parts = path.substring(prefix.length()).split("/", -1);
            if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9][A-Za-z0-9._-]*") ||
                    "latest".equalsIgnoreCase(parts[0]) || !parts[1].equals(mode + "-" + abi + ".apk")) {
                throw new IOException("APK must use an immutable release tag and matching variant");
            }
        } catch (URISyntaxException e) {
            throw new IOException("Invalid APK URL", e);
        }
    }

    static JSONObject parseObject(String json) throws IOException {
        if (json == null) throw new IOException("Missing update JSON");
        try {
            return new JSONObject(json);
        } catch (JSONException e) {
            throw new IOException("Invalid update JSON", e);
        }
    }

    static JSONObject requireObject(JSONObject object, String key) throws IOException {
        Object value = object.opt(key);
        if (!(value instanceof JSONObject)) throw new IOException("Invalid object: " + key);
        return (JSONObject) value;
    }

    static String requireString(JSONObject object, String key) throws IOException {
        Object value = object.opt(key);
        if (!(value instanceof String)) throw new IOException("Invalid string: " + key);
        return (String) value;
    }

    static long requireLong(JSONObject object, String key, long min, long max) throws IOException {
        Object value = object.opt(key);
        if (!(value instanceof Number) || !value.toString().matches("0|[1-9][0-9]*")) {
            throw new IOException("Invalid integer: " + key);
        }
        try {
            long number = Long.parseLong(value.toString());
            if (number < min || number > max) throw new IOException("Out of range: " + key);
            return number;
        } catch (NumberFormatException e) {
            throw new IOException("Out of range: " + key, e);
        }
    }

    public static final class Asset {

        public final String url;
        public final long size;
        public final String sha256;

        private Asset(String url, long size, String sha256) {
            this.url = url;
            this.size = size;
            this.sha256 = sha256;
        }
    }
}
