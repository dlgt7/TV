package com.fongmi.android.tv.update;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class UpdateManifestTest {

    private static final String PACKAGE = "com.fongmi.android.tv";
    private static final String SHA = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    private static final String RELEASE = "https://github.com/dlgt7/TV/releases/download/build-123/";

    private JSONObject manifest(String mode, String abi) throws Exception {
        JSONObject asset = new JSONObject().put("url", RELEASE + mode + "-" + abi + ".apk")
                .put("size", 500000000).put("sha256", SHA);
        return new JSONObject().put("schema", 1).put("code", 987654).put("name", "5.5.5+20261007")
                .put("desc", "播放体验改进").put("packageName", PACKAGE).put("mode", mode)
                .put("assets", new JSONObject().put(abi, asset));
    }

    private UpdateManifest parse(JSONObject json) throws IOException {
        return UpdateManifest.parse(json.toString(), PACKAGE, "leanback", "arm64_v8a");
    }

    private JSONObject asset(JSONObject json) throws Exception {
        return json.getJSONObject("assets").getJSONObject("arm64_v8a");
    }

    @Test
    public void selectsAllSupportedVariants() throws Exception {
        for (String mode : new String[]{"leanback", "mobile"}) {
            for (String abi : new String[]{"arm64_v8a", "armeabi_v7a"}) {
                UpdateManifest result = UpdateManifest.parse(manifest(mode, abi).toString(), PACKAGE, mode, abi);
                assertEquals(987654, result.code);
                assertEquals("5.5.5+20261007", result.name);
                assertEquals("播放体验改进", result.desc);
                assertEquals(RELEASE + mode + "-" + abi + ".apk", result.asset.url);
                assertEquals(500000000L, result.asset.size);
                assertEquals(SHA, result.asset.sha256);
            }
        }
    }

    @Test
    public void selectsCurrentAbiEvenWhenAnotherAssetIsPresent() throws Exception {
        JSONObject json = manifest("leanback", "arm64_v8a");
        json.getJSONObject("assets").put("armeabi_v7a", new JSONObject().put("url", "bad unused asset"));
        assertEquals(RELEASE + "leanback-arm64_v8a.apk", parse(json).asset.url);
    }

    @Test
    public void acceptsOptionalDescriptionAndNormalizesChecksum() throws Exception {
        JSONObject json = manifest("leanback", "arm64_v8a");
        json.remove("desc");
        asset(json).put("sha256", SHA.toUpperCase(java.util.Locale.ROOT));
        assertEquals("", parse(json).desc);
        assertEquals(SHA, parse(json).asset.sha256);
    }

    @Test
    public void rejectsWrongPackageModeOrMissingAbi() throws Exception {
        String json = manifest("leanback", "arm64_v8a").toString();
        assertThrows(IOException.class, () -> UpdateManifest.parse(json, "other.package", "leanback", "arm64_v8a"));
        assertThrows(IOException.class, () -> UpdateManifest.parse(json, PACKAGE, "mobile", "arm64_v8a"));
        assertThrows(IOException.class, () -> UpdateManifest.parse(json, PACKAGE, "leanback", "armeabi_v7a"));
        assertThrows(IOException.class, () -> UpdateManifest.parse(json, PACKAGE, "leanback", "x86"));
        assertThrows(IOException.class, () -> UpdateManifest.parse(json, PACKAGE, "../mobile", "arm64_v8a"));
    }

    @Test
    public void rejectsMissingOrNonStringFields() throws Exception {
        for (String key : new String[]{"name", "packageName", "mode", "assets", "schema", "code"}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            json.remove(key);
            assertThrows(key, IOException.class, () -> parse(json));
        }
        for (String key : new String[]{"name", "desc", "packageName", "mode"}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            json.put(key, 123);
            assertThrows(key, IOException.class, () -> parse(json));
        }
        JSONObject json = manifest("leanback", "arm64_v8a").put("name", " \t\n");
        assertThrows(IOException.class, () -> parse(json));
    }

    @Test
    public void requiresSupportedSchemaAndPositiveIntegerCode() throws Exception {
        for (Object code : new Object[]{0, -1, 2147483648L, "987654", true, JSONObject.NULL}) {
            JSONObject json = manifest("leanback", "arm64_v8a").put("code", code);
            assertThrows(String.valueOf(code), IOException.class, () -> parse(json));
        }
        for (Object schema : new Object[]{0, 2, "1", true, JSONObject.NULL}) {
            JSONObject json = manifest("leanback", "arm64_v8a").put("schema", schema);
            assertThrows(String.valueOf(schema), IOException.class, () -> parse(json));
        }
        String decimal = manifest("leanback", "arm64_v8a").toString().replace("987654", "987654.0");
        assertThrows(IOException.class, () -> UpdateManifest.parse(decimal, PACKAGE, "leanback", "arm64_v8a"));
    }

    @Test
    public void rejectsMissingInvalidAndOversizedAssets() throws Exception {
        for (Object size : new Object[]{0, -1, UpdateManifest.MAX_ASSET_BYTES + 1, "100", 1.5, true, JSONObject.NULL}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            asset(json).put("size", size);
            assertThrows(String.valueOf(size), IOException.class, () -> parse(json));
        }
        for (String sha : new String[]{"", SHA.substring(1), SHA + "0", SHA.replace('a', 'g'), " " + SHA}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            asset(json).put("sha256", sha);
            assertThrows(sha, IOException.class, () -> parse(json));
        }
        for (String key : new String[]{"url", "size", "sha256"}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            asset(json).remove(key);
            assertThrows(key, IOException.class, () -> parse(json));
        }
    }

    @Test
    public void permitsSizeAndVersionUpperBounds() throws Exception {
        JSONObject json = manifest("leanback", "arm64_v8a").put("code", Integer.MAX_VALUE);
        asset(json).put("size", UpdateManifest.MAX_ASSET_BYTES);
        assertEquals(Integer.MAX_VALUE, parse(json).code);
        assertEquals(UpdateManifest.MAX_ASSET_BYTES, parse(json).asset.size);
    }

    @Test
    public void rejectsWrongOwnerMutableAssetsAndAmbiguousUrls() throws Exception {
        String file = "leanback-arm64_v8a.apk";
        for (String url : new String[]{
                "http://github.com/dlgt7/TV/releases/download/build-123/" + file,
                "https://github.com/FongMi/TV/releases/download/build-123/" + file,
                "https://github.com/dlgt7/TV/releases/latest/download/" + file,
                "https://github.com/dlgt7/TV/releases/download/latest/" + file,
                "https://github.com/dlgt7/TV/releases/download/LATEST/" + file,
                "https://github.com/dlgt7/TV/releases/download/../" + file,
                "https://github.com/dlgt7/TV/releases/download/%2e%2e/" + file,
                "https://github.com/dlgt7/TV/releases/download/build-123%2fother/" + file,
                RELEASE + "../build-123/" + file,
                RELEASE + file + "?download=true", RELEASE + file + "#fragment",
                RELEASE + file + "/", RELEASE + "mobile-arm64_v8a.apk", RELEASE + "leanback-armeabi_v7a.apk",
                "https://user@github.com/dlgt7/TV/releases/download/build-123/" + file,
                "https://github.com:443/dlgt7/TV/releases/download/build-123/" + file,
                "https://github.com.evil.test/dlgt7/TV/releases/download/build-123/" + file,
                "https://gh-proxy.com/" + RELEASE + file}) {
            JSONObject json = manifest("leanback", "arm64_v8a");
            asset(json).put("url", url);
            assertThrows(url, IOException.class, () -> parse(json));
        }
    }

    @Test
    public void rejectsMirrorHtmlMalformedOrEmptyJson() {
        for (String json : new String[]{null, "", "<html>Proxy error</html>", "[]", "{broken"}) {
            assertThrows(IOException.class, () -> UpdateManifest.parse(json, PACKAGE, "leanback", "arm64_v8a"));
        }
    }
}
