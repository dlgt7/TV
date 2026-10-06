package com.github.catvod.net.ech;

import static org.junit.Assert.*;

import org.junit.Test;

public class CloudflareAddressRangesTest {
    @Test public void ipv4IncludesBothEdgesAndRejectsAdjacentAddresses() {
        assertTrue(CloudflareAddressRanges.contains(v4(173, 245, 48, 0)));
        assertTrue(CloudflareAddressRanges.contains(v4(173, 245, 63, 255)));
        assertFalse(CloudflareAddressRanges.contains(v4(173, 245, 47, 255)));
        assertFalse(CloudflareAddressRanges.contains(v4(173, 245, 64, 0)));
        assertTrue(CloudflareAddressRanges.contains(v4(104, 16, 0, 0)));
        assertTrue(CloudflareAddressRanges.contains(v4(104, 27, 255, 255)));
        assertFalse(CloudflareAddressRanges.contains(v4(104, 15, 255, 255)));
        assertFalse(CloudflareAddressRanges.contains(v4(104, 28, 0, 0)));
        assertTrue(CloudflareAddressRanges.contains(v4(172, 71, 255, 255)));
        assertFalse(CloudflareAddressRanges.contains(v4(172, 72, 0, 0)));
        assertFalse(CloudflareAddressRanges.contains(v4(172, 16, 0, 1)));
    }

    @Test public void ipv6IncludesNonByteAlignedBoundaries() {
        assertTrue(CloudflareAddressRanges.contains(v6(0x2a06, 0x98c0, false)));
        assertTrue(CloudflareAddressRanges.contains(v6(0x2a06, 0x98c7, true)));
        assertFalse(CloudflareAddressRanges.contains(v6(0x2a06, 0x98bf, true)));
        assertFalse(CloudflareAddressRanges.contains(v6(0x2a06, 0x98c8, false)));
        assertTrue(CloudflareAddressRanges.contains(v6(0x2606, 0x4700, true)));
        assertFalse(CloudflareAddressRanges.contains(v6(0x2606, 0x4701, false)));
        assertFalse(CloudflareAddressRanges.contains(v6(0x2001, 0x4860, false)));
    }

    @Test public void mappedIpv4UsesItsIpv4RangeAndCompatibleIpv4DoesNot() {
        byte[] address = new byte[16];
        address[10] = address[11] = (byte) 0xff;
        System.arraycopy(v4(104, 16, 1, 2), 0, address, 12, 4);
        assertTrue(CloudflareAddressRanges.contains(address));
        address[12] = 8;
        assertFalse(CloudflareAddressRanges.contains(address));
        address[12] = 104;
        address[10] = address[11] = 0;
        assertFalse(CloudflareAddressRanges.contains(address));
    }

    @Test public void rejectsUnknownAndMalformedAddresses() {
        assertFalse(CloudflareAddressRanges.contains(null));
        assertFalse(CloudflareAddressRanges.contains(new byte[0]));
        assertFalse(CloudflareAddressRanges.contains(new byte[5]));
        assertFalse(CloudflareAddressRanges.contains(new byte[16]));
        assertFalse(CloudflareAddressRanges.contains(v4(1, 1, 1, 1)));
        assertFalse(CloudflareAddressRanges.contains(v4(8, 8, 8, 8)));
        assertFalse(CloudflareAddressRanges.contains(v4(127, 0, 0, 1)));
    }

    private static byte[] v4(int a, int b, int c, int d) {
        return new byte[]{(byte) a, (byte) b, (byte) c, (byte) d};
    }

    private static byte[] v6(int a, int b, boolean lastAddress) {
        byte[] address = new byte[16];
        if (lastAddress) java.util.Arrays.fill(address, (byte) 0xff);
        address[0] = (byte) (a >> 8); address[1] = (byte) a;
        address[2] = (byte) (b >> 8); address[3] = (byte) b;
        return address;
    }
}
