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

public class EchDnsParserTest {
    private static final int ID = 0x1342;
    private static final String HOST = "example.com";
    // Structural ECHConfigList fixture; validation of config contents belongs to TLS.
    private static final byte[] ECH = bytes(0, 5, 0xfa, 0xfa, 0, 1, 7);

    @Test
    public void buildsHttpsQuestionWithNormalizedAsciiHost() {
        byte[] expected = concat(bytes(0x13, 0x42, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0),
                name(HOST), bytes(0, 65, 0, 1));
        assertArrayEquals(expected, EchDnsParser.buildQuery(ID, "EXAMPLE.COM."));
    }

    @Test
    public void rejectsInvalidQueryNamesAndIds() {
        String[] invalid = {null, "", ".", "a..com", "a.com..", "http://example.com",
                "é.com", "a b.com", "a\\b.com", repeat('a', 64) + ".com",
                repeat('a', 63) + "." + repeat('b', 63) + "." + repeat('c', 63)
                        + "." + repeat('d', 63)};
        for (String host : invalid) {
            try {
                EchDnsParser.buildQuery(ID, host);
                fail("Accepted invalid query name");
            } catch (IllegalArgumentException expected) {
                assertEquals("dns_name", expected.getMessage());
            }
        }
        for (int id : new int[]{-1, 65536}) {
            try {
                EchDnsParser.buildQuery(id, HOST);
                fail("Accepted invalid ID");
            } catch (IllegalArgumentException expected) {
                assertEquals("dns_id", expected.getMessage());
            }
        }
    }

    @Test
    public void acceptsRootTargetAndPreservesUnknownEchVersion() {
        EchDnsParser.Result result = parse(response(service(1, ".", 120, param(5, ECH))));
        assertTrue(result.hasEch());
        assertFalse(result.hasAlias());
        assertArrayEquals(ECH, result.echConfigList);
        assertEquals(120, result.ttlSeconds);
        assertNull(result.reason);
    }

    @Test
    public void acceptsSameHostTargetCaseInsensitively() {
        assertTrue(parse(response(service(1, "EXAMPLE.COM", 1, param(5, ECH)))).hasEch());
    }

    @Test
    public void preservesZeroAndValidTtlsButDoesNotCacheReservedHighBitTtls() {
        assertEquals(0x7fffffffL, parse(response(service(1, ".", 0x7fffffffL,
                param(5, ECH)))).ttlSeconds);
        assertEquals(0, parse(response(service(1, ".", 0xffffffffL,
                param(5, ECH)))).ttlSeconds);
        assertEquals(0, parse(response(service(1, ".", 0, param(5, ECH)))).ttlSeconds);
    }

    @Test
    public void rejectsIdQuestionTypeAndClassMismatch() {
        byte[] valid = response(service(1, ".", 1, param(5, ECH)));
        byte[] changed = valid.clone();
        changed[1] ^= 1;
        assertRejected("id_mismatch", changed);
        changed = valid.clone();
        changed[13] = 'z';
        assertRejected("question_mismatch", changed);
        changed = valid.clone();
        changed[EchDnsParser.buildQuery(ID, HOST).length - 3] = 1;
        assertRejected("question_mismatch", changed);
        changed = valid.clone();
        changed[EchDnsParser.buildQuery(ID, HOST).length - 1] = 3;
        assertRejected("question_mismatch", changed);
    }

    @Test
    public void rejectsWrongFlagsCountsRcodeAndTruncation() {
        int[] flags = {0x0100, 0x8980, 0x81c0, 0x8380, 0x8183};
        String[] reasons = {"invalid_flags", "invalid_flags", "invalid_flags", "truncated", "dns_rcode"};
        for (int i = 0; i < flags.length; i++) {
            byte[] wire = response(service(1, ".", 1, param(5, ECH)));
            set16(wire, 2, flags[i]);
            assertRejected(reasons[i], wire);
        }
        byte[] wire = response();
        set16(wire, 4, 2);
        assertRejected("question_count", wire);
        wire = response();
        set16(wire, 6, 257);
        assertRejected("record_limit", wire);
    }

    @Test
    public void rejectsTrailingBytesAndAllTruncatedPrefixes() {
        byte[] wire = response(service(1, ".", 1, param(5, ECH)));
        assertRejected("trailing_data", concat(wire, bytes(0)));
        for (int size = 0; size < wire.length; size++) {
            EchDnsParser.Result result = parse(Arrays.copyOf(wire, size));
            assertNotNull("Prefix length " + size, result.reason);
            assertFalse(result.hasEch());
            assertFalse(result.hasAlias());
        }
    }

    @Test
    public void rejectsOversizeNullAndInvalidExpectations() {
        assertRejected("message_size", null);
        assertRejected("message_size", new byte[65536]);
        byte[] wire = response();
        assertEquals("invalid_expectation", EchDnsParser.parse(wire, -1, HOST, 443).reason);
        assertEquals("invalid_expectation", EchDnsParser.parse(wire, ID, HOST, 0).reason);
        assertEquals("invalid_expectation", EchDnsParser.parse(wire, ID, null, 443).reason);
    }

    @Test
    public void rejectsCompressedHttpsTarget() {
        assertRejected("compressed_target", response(rr(HOST, 65, 1, 10,
                concat(bytes(0, 1, 0xc0, 0x0c), param(5, ECH)))));
    }

    @Test
    public void rejectsForwardSelfAndHeaderCompressionPointers() {
        for (int destination : new int[]{0, 12, 14}) {
            byte[] wire = response();
            wire[12] = (byte) 0xc0;
            wire[13] = (byte) destination;
            assertRejected("name_pointer", wire);
        }
    }

    @Test
    public void rejectsReservedLabelCodesAndNonAsciiNames() {
        byte[] wire = response();
        wire[12] = 0x40;
        assertRejected("name_label", wire);
        wire = response();
        wire[13] = (byte) 0xff;
        assertRejected("name_character", wire);
    }

    @Test
    public void rejectsTooLongExpandedOwnerName() {
        String longName = repeat('a', 63) + "." + repeat('b', 63) + "."
                + repeat('c', 63) + "." + repeat('d', 63);
        assertRejected("name_length", response(rr(longName, 65, 1, 1,
                concat(bytes(0, 1, 0), param(5, ECH)))));
    }

    @Test
    public void acceptsCompressedAnswerOwner() {
        byte[] record = concat(bytes(0xc0, 0x0c), bytes(0, 65, 0, 1, 0, 0, 0, 10),
                length16(concat(bytes(0, 1, 0), param(5, ECH))));
        assertTrue(parse(response(record)).hasEch());
    }

    @Test
    public void onlyUsesMatchingInClassAnswerRecords() {
        assertRejected("no_https_record", response(rr("elsewhere.example", 65, 1, 1,
                concat(bytes(0, 1, 0), param(5, ECH)))));
        assertRejected("no_https_record", response(rr(HOST, 65, 3, 1,
                concat(bytes(0, 1, 0), param(5, ECH)))));
        byte[] wire = response(service(1, ".", 1, param(5, ECH)));
        set16(wire, 6, 0);
        set16(wire, 10, 1);
        assertRejected("no_https_record", wire);
    }

    @Test
    public void validatesEdnsExtendedRcodeVersionAndOptionFraming() {
        byte[] answer = service(1, ".", 1, param(5, ECH));
        assertTrue(parse(withAdditional(answer, rr(".", 41, 1232, 0, bytes()))).hasEch());
        assertRejected("invalid_edns", withAdditional(answer, rr(".", 41, 1232, 0x01000000L, bytes())));
        assertRejected("invalid_edns", withAdditional(answer, rr(".", 41, 1232, 0x00010000L, bytes())));
        assertRejected("record_bounds", withAdditional(answer, rr(".", 41, 1232, 0, bytes(0, 1, 0, 1))));
    }

    @Test
    public void validatesEchListAndConfigLengthFraming() {
        byte[][] invalid = {bytes(), bytes(0, 0), bytes(0, 1, 0),
                bytes(0, 5, 0xfa, 0xfa, 0, 2, 7), bytes(0, 5, 0xfa, 0xfa, 0, 0, 7),
                bytes(0, 4, 0xfa, 0xfa, 0, 1, 7), concat(ECH, bytes(0))};
        for (byte[] ech : invalid) assertRejected("invalid_ech", response(service(1, ".", 1, param(5, ech))));
        byte[] multiple = bytes(0, 10, 0xfa, 0xfa, 0, 1, 7, 0xfb, 0xfb, 0, 1, 8);
        assertArrayEquals(multiple, parse(response(service(1, ".", 1, param(5, multiple)))).echConfigList);
    }

    @Test
    public void rejectsDuplicateOutOfOrderAndTruncatedParameters() {
        assertRejected("parameter_order", response(service(1, ".", 1, param(5, ECH), param(5, ECH))));
        assertRejected("parameter_order", response(service(1, ".", 1, param(5, ECH), param(3, bytes(1, 187)))));
        assertRejected("record_bounds", response(service(1, ".", 1, bytes(0, 5, 0))));
        assertRejected("record_bounds", response(service(1, ".", 1, bytes(0, 5, 0, 8, 1))));
    }

    @Test
    public void validatesMandatoryKeysAndAllowsUnknownOptionalKeys() {
        assertTrue(parse(response(service(1, ".", 1, param(0, bytes(0, 5)), param(5, ECH),
                param(65400, bytes(1, 2))))).hasEch());
        assertRejected("unsupported_mandatory", response(service(1, ".", 1,
                param(0, bytes(0, 7)), param(5, ECH), param(7, bytes(1)))));
        assertRejected("missing_mandatory", response(service(1, ".", 1,
                param(0, bytes(0, 3)), param(5, ECH))));
        assertRejected("invalid_parameter_key", response(service(1, ".", 1,
                param(5, ECH), param(65535, bytes()))));
        for (byte[] mandatory : new byte[][]{bytes(), bytes(0), bytes(0, 0), bytes(0, 5, 0, 5), bytes(0, 5, 0, 1)}) {
            assertRejected("invalid_mandatory", response(service(1, ".", 1,
                    param(0, mandatory), param(1, alpn("h2")), param(5, ECH))));
        }
    }

    @Test
    public void rejectsUnsupportedLowestPriorityInsteadOfUsingAnotherService() {
        assertRejected("unsupported_mandatory", response(
                service(1, ".", 1, param(0, bytes(0, 7)), param(5, ECH), param(7, bytes(1))),
                service(2, ".", 1, param(5, ECH))));
        assertRejected("no_ech", response(service(1, ".", 1), service(2, ".", 1, param(5, ECH))));
    }

    @Test
    public void selectsLowestPriorityAndRejectsAmbiguousSamePriority() {
        byte[] different = ECH.clone();
        different[different.length - 1] = 8;
        assertArrayEquals(ECH, parse(response(service(3, ".", 1, param(5, different)),
                service(1, ".", 20, param(5, ECH)))).echConfigList);
        assertRejected("ambiguous_service", response(service(1, ".", 1, param(5, ECH)),
                service(1, ".", 1, param(5, different))));
        assertRejected("no_ech", response(service(1, ".", 1, param(5, ECH)), service(1, ".", 1)));
        assertEquals(10, parse(response(service(1, ".", 20, param(5, ECH)),
                service(1, ".", 10, param(5, ECH)))).ttlSeconds);
    }

    @Test
    public void rejectsAlternateServiceTargetAndPort() {
        assertRejected("unsupported_service_target", response(service(1, "other.example", 1, param(5, ECH))));
        assertRejected("unsupported_port", response(service(1, ".", 1, param(3, bytes(0x20, 0xfb)), param(5, ECH))));
        assertTrue(parse(response(service(1, ".", 1, param(3, bytes(1, 187)), param(5, ECH)))).hasEch());
        assertRejected("invalid_port", response(service(1, ".", 1, param(3, bytes(1)), param(5, ECH))));
    }

    @Test
    public void acceptsH2Http11AndDefaultAlpnButRejectsH3OnlyNoDefault() {
        for (String protocol : new String[]{"h2", "http/1.1"}) {
            assertTrue(parse(response(service(1, ".", 1,
                    param(1, alpn(protocol)), param(2, bytes()), param(5, ECH)))).hasEch());
        }
        assertTrue(parse(response(service(1, ".", 1, param(1, alpn("h3")), param(5, ECH)))).hasEch());
        assertRejected("unsupported_alpn", response(service(1, ".", 1,
                param(1, alpn("h3")), param(2, bytes()), param(5, ECH))));
        assertRejected("missing_alpn", response(service(1, ".", 1, param(2, bytes()), param(5, ECH))));
    }

    @Test
    public void rejectsMalformedAlpnAndNoDefaultValues() {
        for (byte[] protocols : new byte[][]{bytes(), bytes(0), bytes(3, 'h', '2'), concat(alpn("h2"), alpn("h2"))}) {
            assertRejected("invalid_alpn", response(service(1, ".", 1, param(1, protocols), param(5, ECH))));
        }
        assertRejected("invalid_no_default_alpn", response(service(1, ".", 1,
                param(1, alpn("h2")), param(2, bytes(0)), param(5, ECH))));
    }

    @Test
    public void validatesAddressHintLengths() {
        assertTrue(parse(response(service(1, ".", 1, param(4, bytes(127, 0, 0, 1)),
                param(5, ECH), param(6, new byte[16])))).hasEch());
        for (byte[] hint : new byte[][]{bytes(), new byte[3], new byte[5]}) {
            assertRejected("invalid_ipv4hint", response(service(1, ".", 1, param(4, hint), param(5, ECH))));
        }
        assertRejected("invalid_ipv6hint", response(service(1, ".", 1, param(5, ECH), param(6, new byte[15]))));
    }

    @Test
    public void returnsCnameAndHttpsAliasWithTtlForSubsequentQuery() {
        EchDnsParser.Result cname = parse(response(rr(HOST, 5, 1, 60, name("alias.example"))));
        assertEquals("alias.example", cname.aliasTarget);
        assertEquals(60, cname.ttlSeconds);
        assertNull(cname.reason);
        EchDnsParser.Result alias = parse(response(service(0, "alias.example", 90)));
        assertEquals("alias.example", alias.aliasTarget);
        assertEquals(90, alias.ttlSeconds);
        assertTrue(alias.hasAlias());
        assertFalse(alias.hasEch());
    }

    @Test
    public void followsCnameAndHttpsAliasChainsPresentInAnswers() {
        byte[] destination = rr("dest.example", 65, 1, 50, concat(bytes(0, 1, 0), param(5, ECH)));
        EchDnsParser.Result result = parse(response(
                rr(HOST, 5, 1, 60, name("alias.example")),
                rr("alias.example", 65, 1, 20, concat(bytes(0, 0), name("dest.example"))),
                destination));
        assertArrayEquals(ECH, result.echConfigList);
        assertEquals(20, result.ttlSeconds);
    }

    @Test
    public void ignoresAliasParametersAndAliasModeWinsOverServiceMode() {
        EchDnsParser.Result result = parse(response(service(1, ".", 1, param(5, ECH)),
                service(0, "alias.example", 10, param(0, bytes(1)))));
        assertEquals("alias.example", result.aliasTarget);
        assertEquals(10, result.ttlSeconds);
    }

    @Test
    public void rejectsAliasLoopsRootTargetsAndConflicts() {
        assertRejected("alias_loop", response(service(0, HOST, 1)));
        assertRejected("alias_loop", response(service(0, "alias.example", 1),
                rr("alias.example", 5, 1, 1, name(HOST))));
        assertRejected("service_unavailable", response(service(0, ".", 1)));
        assertRejected("conflicting_alias", response(service(0, "one.example", 1), service(0, "two.example", 1)));
        assertRejected("conflicting_cname", response(rr(HOST, 5, 1, 1, name("one.example")),
                rr(HOST, 5, 1, 1, name("two.example"))));
        assertRejected("cname_conflict", response(rr(HOST, 5, 1, 1, name("one.example")),
                service(1, ".", 1, param(5, ECH))));
    }

    @Test
    public void enforcesAliasHopLimit() {
        byte[][] aliases = new byte[9][];
        for (int i = 0; i < aliases.length; i++) {
            aliases[i] = rr(i == 0 ? HOST : "hop" + i + ".example", 5, 1, 1,
                    name("hop" + (i + 1) + ".example"));
        }
        assertRejected("alias_limit", response(aliases));
        assertEquals("hop8.example", parse(response(Arrays.copyOf(aliases, 8))).aliasTarget);
    }

    @Test
    public void handlesCompressedCnameAndRejectsItsTrailingBytes() {
        // The CNAME is the compressed suffix "com" at offset 20 in the question.
        assertEquals("com", parse(response(rr(HOST, 5, 1, 1, bytes(0xc0, 20)))).aliasTarget);
        assertRejected("cname_length", response(rr(HOST, 5, 1, 1, concat(name("alias.example"), bytes(0)))));
    }

    @Test
    public void boundedMalformedInputsNeverThrowUncheckedExceptions() {
        Random random = new Random(0x454348L);
        byte[] seed = response(service(1, ".", 1, param(5, ECH)));
        for (int i = 0; i < 4000; i++) {
            byte[] wire;
            if ((i & 1) == 0) {
                wire = new byte[random.nextInt(1024)];
                random.nextBytes(wire);
            } else {
                wire = seed.clone();
                for (int j = 0; j < 4; j++) wire[random.nextInt(wire.length)] = (byte) random.nextInt(256);
            }
            EchDnsParser.Result result = parse(wire);
            assertNotNull(result);
            if (result.reason != null) {
                assertNull(result.echConfigList);
                assertNull(result.aliasTarget);
            }
        }
    }

    private static EchDnsParser.Result parse(byte[] wire) {
        return EchDnsParser.parse(wire, ID, HOST, 443);
    }

    private static void assertRejected(String reason, byte[] wire) {
        EchDnsParser.Result result = parse(wire);
        assertEquals(reason, result.reason);
        assertNull(result.echConfigList);
        assertNull(result.aliasTarget);
        assertEquals(0, result.ttlSeconds);
    }

    private static byte[] response(byte[]... records) {
        byte[] question = EchDnsParser.buildQuery(ID, HOST);
        set16(question, 2, 0x8180);
        set16(question, 6, records.length);
        return concat(question, concat(records));
    }

    private static byte[] withAdditional(byte[] answer, byte[] additional) {
        byte[] wire = response(answer, additional);
        set16(wire, 6, 1);
        set16(wire, 10, 1);
        return wire;
    }

    private static byte[] service(int priority, String target, long ttl, byte[]... parameters) {
        return rr(HOST, 65, 1, ttl, concat(bytes(priority >>> 8, priority), name(target), concat(parameters)));
    }

    private static byte[] rr(String owner, int type, int dnsClass, long ttl, byte[] rdata) {
        return concat(name(owner), bytes(type >>> 8, type, dnsClass >>> 8, dnsClass,
                (int) (ttl >>> 24), (int) (ttl >>> 16), (int) (ttl >>> 8), (int) ttl), length16(rdata));
    }

    private static byte[] param(int key, byte[] value) {
        return concat(bytes(key >>> 8, key), length16(value));
    }

    private static byte[] name(String host) {
        if (host.equals(".")) return bytes(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (String label : host.split("\\.")) {
            out.write(label.length());
            for (int i = 0; i < label.length(); i++) out.write(label.charAt(i));
        }
        out.write(0);
        return out.toByteArray();
    }

    private static byte[] alpn(String protocol) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(protocol.length());
        for (int i = 0; i < protocol.length(); i++) out.write(protocol.charAt(i));
        return out.toByteArray();
    }

    private static byte[] length16(byte[] data) {
        return concat(bytes(data.length >>> 8, data.length), data);
    }

    private static byte[] bytes(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return bytes;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.write(part, 0, part.length);
        return out.toByteArray();
    }

    private static void set16(byte[] bytes, int offset, int value) {
        bytes[offset] = (byte) (value >>> 8);
        bytes[offset + 1] = (byte) value;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
