package com.github.catvod.net.ech;

/**
 * Cloudflare's published network ranges, retrieved 2026-10-06 from
 * https://www.cloudflare.com/ips-v4/ and https://www.cloudflare.com/ips-v6/.
 * Membership permits trying its ECH configuration; it does not guarantee ECH support.
 * Operates only on address bytes and never performs DNS or reverse-DNS lookups.
 */
final class CloudflareAddressRanges {
    private static final Prefix[] RANGES = {
            v4(173, 245, 48, 0, 20), v4(103, 21, 244, 0, 22),
            v4(103, 22, 200, 0, 22), v4(103, 31, 4, 0, 22),
            v4(141, 101, 64, 0, 18), v4(108, 162, 192, 0, 18),
            v4(190, 93, 240, 0, 20), v4(188, 114, 96, 0, 20),
            v4(197, 234, 240, 0, 22), v4(198, 41, 128, 0, 17),
            v4(162, 158, 0, 0, 15), v4(104, 16, 0, 0, 13),
            v4(104, 24, 0, 0, 14), v4(172, 64, 0, 0, 13),
            v4(131, 0, 72, 0, 22),
            v6(0x2400, 0xcb00, 32), v6(0x2606, 0x4700, 32),
            v6(0x2803, 0xf800, 32), v6(0x2405, 0xb500, 32),
            v6(0x2405, 0x8100, 32), v6(0x2a06, 0x98c0, 29),
            v6(0x2c0f, 0xf248, 32)
    };

    private CloudflareAddressRanges() {
    }

    static boolean contains(byte[] address) {
        if (address == null || (address.length != 4 && address.length != 16)) return false;
        int offset = 0;
        if (address.length == 16 && isMappedIpv4(address)) offset = 12;
        int size = address.length - offset;
        for (Prefix range : RANGES) {
            if (range.address.length != size) continue;
            int wholeBytes = range.bits / 8;
            boolean matches = true;
            for (int i = 0; i < wholeBytes; i++) {
                if (range.address[i] != address[offset + i]) {
                    matches = false;
                    break;
                }
            }
            int remaining = range.bits % 8;
            if (matches && (remaining == 0 || ((range.address[wholeBytes]
                    ^ address[offset + wholeBytes]) & (0xff << (8 - remaining))) == 0)) return true;
        }
        return false;
    }

    private static boolean isMappedIpv4(byte[] address) {
        for (int i = 0; i < 10; i++) if (address[i] != 0) return false;
        return address[10] == (byte) 0xff && address[11] == (byte) 0xff;
    }

    private static Prefix v4(int a, int b, int c, int d, int bits) {
        return new Prefix(new byte[]{(byte) a, (byte) b, (byte) c, (byte) d}, bits);
    }

    private static Prefix v6(int a, int b, int bits) {
        byte[] address = new byte[16];
        address[0] = (byte) (a >> 8);
        address[1] = (byte) a;
        address[2] = (byte) (b >> 8);
        address[3] = (byte) b;
        return new Prefix(address, bits);
    }

    private static final class Prefix {
        final byte[] address;
        final int bits;

        Prefix(byte[] address, int bits) {
            this.address = address;
            this.bits = bits;
        }
    }
}
