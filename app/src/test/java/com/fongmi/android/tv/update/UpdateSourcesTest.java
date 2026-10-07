package com.fongmi.android.tv.update;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class UpdateSourcesTest {

    @Test
    public void downloadsUseTwoMirrorsThenOriginalGithub() {
        String url = "https://github.com/wobuhui666/TV/releases/download/build-123/leanback-arm64_v8a.apk";
        List<String> sources = UpdateSources.mirrors(url);
        assertEquals(Arrays.asList("https://gh-proxy.com/" + url, "https://ghfast.top/" + url, url), sources);
        assertEquals(sources.size(), new HashSet<>(sources).size());
    }

    @Test
    public void discoversEachModeOnOwnLatestRelease() {
        for (String mode : new String[]{"leanback", "mobile"}) {
            String url = "https://github.com/wobuhui666/TV/releases/latest/download/" + mode + ".json";
            assertEquals(UpdateSources.mirrors(url), UpdateSources.manifests(mode));
        }
    }

    @Test
    public void cloudPolicyUsesDedicatedOwnBranchFile() {
        String url = "https://raw.githubusercontent.com/wobuhui666/TV/ui/apple-tv-redesign/ota/policy.json";
        assertEquals(UpdateSources.mirrors(url), UpdateSources.policies());
    }

    @Test
    public void rejectsUnsupportedModesAndUntrustedSources() {
        for (String mode : new String[]{null, "", "../leanback", "tv"}) {
            assertThrows(IllegalArgumentException.class, () -> UpdateSources.manifests(mode));
        }
        for (String url : new String[]{null, "", "https://github.com/FongMi/TV/releases/latest/download/leanback.json",
                "https://github.com/wobuhui666/TV/releases/../other.json",
                "https://github.com/wobuhui666/TV/releases/%2e%2e/other.json",
                "https://github.com/wobuhui666/TV/releases/latest/download/leanback.json?source=other",
                "https://github.com/wobuhui666/TV/releases/latest/download/leanback.json#other",
                "https://gh-proxy.com/https://github.com/wobuhui666/TV/releases/latest/download/leanback.json"}) {
            assertThrows(IllegalArgumentException.class, () -> UpdateSources.mirrors(url));
        }
    }
}
