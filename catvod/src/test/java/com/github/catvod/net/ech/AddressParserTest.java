package com.github.catvod.net.ech;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AddressParserTest {
    private static final int ID = 0x1342;
    private static final String HOST = "example.com";
    private static final byte[] IPV4 = bytes(192, 0, 2, 1);
    private static final byte[] IPV6 = bytes(0x20, 1, 0xd, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1);

    @Test
    public void buildsOnlySupportedQuestionsAndPreservesHttpsDefault() {
        assertArrayEquals(EchDnsParser.buildQuery(ID, HOST, 65), EchDnsParser.buildQuery(ID, HOST));
        for (int type : new int[]{1, 28, 65}) {
            byte[] query = EchDnsParser.buildQuery(ID, "EXAMPLE.COM.", type);
            assertArrayEquals(bytes(0, type, 0, 1), Arrays.copyOfRange(query, query.length - 4, query.length));
        }
        for (int type : new int[]{-1, 0, 5, 255, 65536}) {
            try {
                EchDnsParser.buildQuery(ID, HOST, type);
                fail("Unsupported query type");
            } catch (IllegalArgumentException expected) {
                assertEquals("dns_type", expected.getMessage());
            }
        }
    }

    @Test
    public void readsEachAddressFamilyAndMinimumTtlWithoutUsingOtherTypes() {
        for (int type : new int[]{1, 28}) {
            byte[] address = type == 1 ? IPV4 : IPV6;
            EchDnsParser.AddressResult result = parse(response(type,
                    rr(HOST, 1, 1, 120, IPV4), rr(HOST, 28, 1, 90, IPV6)), type);
            assertNull(result.reason);
            assertNull(result.aliasTarget);
            assertEquals(1, result.addresses.size());
            assertArrayEquals(address, result.addresses.get(0));
            assertEquals(type == 1 ? 120 : 90, result.ttlSeconds);
        }
        EchDnsParser.AddressResult multiple = parse(response(1,
                rr(HOST, 1, 1, 90, IPV4), rr(HOST, 1, 1, 10, bytes(192, 0, 2, 2))), 1);
        assertEquals(2, multiple.addresses.size());
        assertEquals(10, multiple.ttlSeconds);
    }

    @Test
    public void followsUnorderedCnameChainAndUsesMinimumChainTtl() {
        EchDnsParser.AddressResult result = parse(response(1,
                rr("final.example", 1, 1, 90, IPV4),
                rr("alias.example", 5, 1, 20, name("final.example")),
                rr(HOST, 5, 1, 30, name("alias.example"))), 1);
        assertNull(result.reason);
        assertNull(result.aliasTarget);
        assertArrayEquals(IPV4, result.addresses.get(0));
        assertEquals(20, result.ttlSeconds);
    }

    @Test
    public void returnsPendingAliasWithoutTrustingAdditionalAddresses() {
        byte[] wire = message(1, 1, 0, 1,
                rr(HOST, 5, 1, 30, name("alias.example")),
                rr("alias.example", 1, 1, 10, IPV4));
        EchDnsParser.AddressResult result = parse(wire, 1);
        assertNull(result.reason);
        assertTrue(result.addresses.isEmpty());
        assertEquals("alias.example", result.aliasTarget);
        assertTrue(result.hasAlias());
        assertEquals(30, result.ttlSeconds);
    }

    @Test
    public void ignoresUnrelatedAnswerAndAuthorityAndAdditionalAddresses() {
        EchDnsParser.AddressResult result = parse(message(1, 2, 1, 1,
                rr("unrelated.example", 1, 1, 1, bytes(203, 0, 113, 4)),
                rr(HOST, 1, 1, 40, IPV4),
                rr(HOST, 1, 1, 1, bytes(203, 0, 113, 5)),
                rr(HOST, 1, 1, 1, bytes(203, 0, 113, 6))), 1);
        assertNull(result.reason);
        assertEquals(1, result.addresses.size());
        assertArrayEquals(IPV4, result.addresses.get(0));
        assertEquals(40, result.ttlSeconds);
    }

    @Test
    public void distinguishesValidNoDataFromErrorResponses() {
        for (byte[] wire : new byte[][]{response(1), response(1,
                rr(HOST, 28, 1, 10, IPV6)), response(1,
                rr("unrelated.example", 1, 1, 10, IPV4))}) {
            EchDnsParser.AddressResult result = parse(wire, 1);
            assertNull(result.reason);
            assertFalse(result.hasAlias());
            assertTrue(result.addresses.isEmpty());
            assertEquals(0, result.ttlSeconds);
        }
        for (int rcode : new int[]{1, 2, 3, 5}) {
            byte[] wire = response(1);
            set16(wire, 2, 0x8180 | rcode);
            rejected("dns_rcode", wire, 1);
        }
    }

    @Test
    public void rejectsConflictingCnamesAndAnyAddressAlongsideCname() {
        rejected("conflicting_cname", response(1,
                rr(HOST, 5, 1, 20, name("first.example")),
                rr(HOST, 5, 1, 30, name("second.example"))), 1);
        for (int type : new int[]{1, 28}) {
            rejected("cname_conflict", response(1,
                    rr(HOST, 5, 1, 20, name("alias.example")),
                    rr(HOST, type, 1, 30, type == 1 ? IPV4 : IPV6)), 1);
        }
    }

    @Test
    public void acceptsDuplicateConsistentCnamesWithConservativeTtl() {
        EchDnsParser.AddressResult result = parse(response(1,
                rr(HOST, 5, 1, 30, name("alias.example")),
                rr(HOST, 5, 1, 10, name("ALIAS.EXAMPLE")),
                rr("alias.example", 1, 1, 20, IPV4)), 1);
        assertNull(result.reason);
        assertArrayEquals(IPV4, result.addresses.get(0));
        assertEquals(10, result.ttlSeconds);
    }

    @Test
    public void rejectsAliasCyclesRootTargetsAndTooManyHops() {
        rejected("alias_loop", response(1, rr(HOST, 5, 1, 10, name(HOST))), 1);
        rejected("alias_loop", response(1,
                rr(HOST, 5, 1, 10, name("alias.example")),
                rr("alias.example", 5, 1, 10, name(HOST))), 1);
        rejected("invalid_cname", response(1, rr(HOST, 5, 1, 10, name("."))), 1);
        byte[][] records = new byte[9][];
        for (int i = 0; i < records.length; i++) records[i] = rr(i == 0 ? HOST : "a" + i + ".example",
                5, 1, 10, name("a" + (i + 1) + ".example"));
        rejected("alias_limit", response(1, records), 1);
    }

    @Test
    public void acceptsCompressedOwnerAndCnameSuffix() {
        byte[] cname = concat(bytes(0xc0, 0x0c, 0, 5, 0, 1, 0, 0, 0, 10),
                length16(concat(bytes(5, 'a', 'l', 'i', 'a', 's', 0xc0, 0x0c))));
        EchDnsParser.AddressResult result = parse(response(1, cname,
                rr("alias.example.com", 1, 1, 20, IPV4)), 1);
        assertNull(result.reason);
        assertArrayEquals(IPV4, result.addresses.get(0));
    }

    @Test
    public void rejectsHeaderForwardSelfAndNonLabelCompressionPointers() {
        for (int destination : new int[]{0, 12, 14}) {
            byte[] wire = response(1);
            wire[12] = (byte) 0xc0;
            wire[13] = (byte) destination;
            rejected("name_pointer", wire, 1);
        }
        int queryLength = EchDnsParser.buildQuery(ID, HOST, 1).length;
        // The zero QTYPE high byte is not a previously decoded name-label boundary.
        rejected("name_pointer", response(1, concat(bytes(0xc0, queryLength - 4,
                0, 1, 0, 1, 0, 0, 0, 10, 0, 4), IPV4)), 1);
    }

    @Test
    public void rejectsMalformedAddressAndCnameRdataIncludingIgnoredSections() {
        for (int type : new int[]{1, 28}) {
            for (int size : new int[]{0, 3, 5, 15, 17}) {
                rejected("address_length", response(type, rr(HOST, type, 1, 10, new byte[size])), type);
            }
        }
        rejected("cname_length", response(1,
                rr(HOST, 5, 1, 10, concat(name("alias.example"), bytes(0)))), 1);
        rejected("address_length", message(1, 0, 0, 1,
                rr("unrelated.example", 1, 1, 10, bytes(1))), 1);
    }

    @Test
    public void rejectsWrongIdQuestionTypeHostAndClass() {
        byte[] wire = response(1, rr(HOST, 1, 1, 10, IPV4));
        byte[] changed = wire.clone();
        changed[1] ^= 1;
        rejected("id_mismatch", changed, 1);
        changed = wire.clone();
        changed[13] = 'z';
        rejected("question_mismatch", changed, 1);
        rejected("question_mismatch", wire, 28);
        changed = wire.clone();
        changed[EchDnsParser.buildQuery(ID, HOST, 1).length - 1] = 3;
        rejected("question_mismatch", changed, 1);
        rejected("answer_class", response(1, rr(HOST, 1, 3, 10, IPV4)), 1);
        rejected("answer_class", response(1, rr(HOST, 5, 3, 10, name("alias.example"))), 1);
    }

    @Test
    public void rejectsInvalidFlagsCountsAndTruncation() {
        int[] flags = {0x0100, 0x8980, 0x81c0, 0x8380};
        String[] reasons = {"invalid_flags", "invalid_flags", "invalid_flags", "truncated"};
        for (int i = 0; i < flags.length; i++) {
            byte[] wire = response(1);
            set16(wire, 2, flags[i]);
            rejected(reasons[i], wire, 1);
        }
        byte[] wire = response(1);
        set16(wire, 4, 0);
        rejected("question_count", wire, 1);
        wire = response(1);
        set16(wire, 6, 257);
        rejected("record_limit", wire, 1);
    }

    @Test
    public void validatesEdnsPlacementOwnerVersionRcodeFlagsAndOptionBounds() {
        byte[] answer = rr(HOST, 1, 1, 10, IPV4);
        assertNull(parse(message(1, 1, 0, 1, answer, rr(".", 41, 1232, 0x8000, bytes())), 1).reason);
        for (long ttl : new long[]{0x01000000L, 0x00010000L, 1}) {
            rejected("invalid_edns", message(1, 1, 0, 1, answer, rr(".", 41, 1232, ttl, bytes())), 1);
        }
        rejected("invalid_edns", response(1, rr(".", 41, 1232, 0, bytes())), 1);
        rejected("invalid_edns", message(1, 1, 0, 1, answer, rr(HOST, 41, 1232, 0, bytes())), 1);
        rejected("invalid_edns", message(1, 1, 0, 2, answer,
                rr(".", 41, 1232, 0, bytes()), rr(".", 41, 1232, 0, bytes())), 1);
        rejected("record_bounds", message(1, 1, 0, 1, answer,
                rr(".", 41, 1232, 0, bytes(0, 1, 0, 2, 1))), 1);
    }

    @Test
    public void rejectsAllTruncatedPrefixesTrailingBytesAndInvalidExpectations() {
        byte[] wire = response(1, rr(HOST, 1, 1, 10, IPV4));
        for (int size = 0; size < wire.length; size++) {
            EchDnsParser.AddressResult result = parse(Arrays.copyOf(wire, size), 1);
            assertNotNull("Prefix " + size, result.reason);
            assertTrue(result.addresses.isEmpty());
            assertFalse(result.hasAlias());
        }
        rejected("trailing_data", concat(wire, bytes(0)), 1);
        rejected("message_size", null, 1);
        rejected("message_size", new byte[65536], 1);
        rejected("invalid_expectation", wire, 65);
        assertEquals("invalid_expectation", EchDnsParser.parseAddresses(wire, -1, HOST, 1).reason);
        assertEquals("invalid_expectation", EchDnsParser.parseAddresses(wire, ID, null, 1).reason);
    }

    @Test
    public void preservesZeroTtlAndTreatsReservedHighBitAsZero() {
        for (long ttl : new long[]{0, 0x7fffffffL, 0xffffffffL}) {
            assertEquals(ttl > 0x7fffffffL ? 0 : ttl,
                    parse(response(1, rr(HOST, 1, 1, ttl, IPV4)), 1).ttlSeconds);
        }
    }

    @Test
    public void resultDoesNotAliasInputWireAndListCannotBeExtended() {
        byte[] wire = response(1, rr(HOST, 1, 1, 10, IPV4));
        EchDnsParser.AddressResult result = parse(wire, 1);
        Arrays.fill(wire, (byte) 0);
        assertArrayEquals(IPV4, result.addresses.get(0));
        try {
            result.addresses.add(IPV6);
            fail("Mutable address list");
        } catch (UnsupportedOperationException expected) {
            assertEquals(1, result.addresses.size());
        }
    }

    @Test
    public void boundedRandomWireNeverEscapesAsUncheckedFailure() {
        Random random = new Random(641903);
        for (int i = 0; i < 2000; i++) {
            byte[] wire = new byte[random.nextInt(512)];
            random.nextBytes(wire);
            EchDnsParser.AddressResult result = parse(wire, i % 2 == 0 ? 1 : 28);
            assertNotNull(result);
            assertNotNull(result.reason);
            assertTrue(result.addresses.isEmpty());
        }
    }

    private static EchDnsParser.AddressResult parse(byte[] wire, int type) {
        return EchDnsParser.parseAddresses(wire, ID, HOST, type);
    }

    private static void rejected(String reason, byte[] wire, int type) {
        EchDnsParser.AddressResult result = parse(wire, type);
        assertEquals(reason, result.reason);
        assertTrue(result.addresses.isEmpty());
        assertNull(result.aliasTarget);
    }

    private static byte[] response(int type, byte[]... records) {
        return message(type, records.length, 0, 0, records);
    }

    private static byte[] message(int type, int answers, int authorities, int additional,
                                  byte[]... records) {
        byte[] query = EchDnsParser.buildQuery(ID, HOST, type);
        set16(query, 2, 0x8180);
        set16(query, 6, answers);
        set16(query, 8, authorities);
        set16(query, 10, additional);
        return concat(query, concat(records));
    }

    private static byte[] rr(String owner, int type, int dnsClass, long ttl, byte[] value) {
        return concat(name(owner), bytes(type >>> 8, type, dnsClass >>> 8, dnsClass,
                (int) (ttl >>> 24), (int) (ttl >>> 16), (int) (ttl >>> 8), (int) ttl), length16(value));
    }

    private static byte[] name(String value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!value.equals(".")) for (String label : value.split("\\.")) {
            out.write(label.length());
            for (int i = 0; i < label.length(); i++) out.write(label.charAt(i));
        }
        out.write(0);
        return out.toByteArray();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) result[i] = (byte) values[i];
        return result;
    }

    private static byte[] length16(byte[] value) {
        return concat(bytes(value.length >>> 8, value.length), value);
    }

    private static byte[] concat(byte[]... arrays) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] array : arrays) out.write(array, 0, array.length);
        return out.toByteArray();
    }

    private static void set16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }
}
