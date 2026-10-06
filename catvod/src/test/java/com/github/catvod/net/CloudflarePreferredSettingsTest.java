package com.github.catvod.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class CloudflarePreferredSettingsTest {
    @Test
    public void emptyValueDisablesRoutingAndHostnameIsCanonicalized() {
        assertEquals("", CloudflarePreferredSettings.normalize(null));
        assertEquals("", CloudflarePreferredSettings.normalize("  "));
        assertEquals("edge.example.com", CloudflarePreferredSettings.normalize(" EDGE.Example.COM. "));
        assertEquals("xn--fsqu00a.xn--0zwm56d", CloudflarePreferredSettings.normalize("例子.测试"));
    }

    @Test
    public void rejectsUrlsCredentialsPortsAddressesAndInvalidLabels() {
        for (String value : new String[]{"https://edge.example.com", "edge.example.com:443",
                "user@edge.example.com", "edge.example.com/path", "edge.example.com?key=value",
                "edge.example.com#fragment", "127.0.0.1", "127。0。0。1", "[2606:4700::1111]",
                "2606:4700::1111", "localhost", ".example.com", "a..example.com",
                "-a.example.com", "a-.example.com", "bad name.example.com", "a_b.example.com"}) {
            assertThrows(value, IllegalArgumentException.class,
                    () -> CloudflarePreferredSettings.normalize(value));
        }
    }
}
