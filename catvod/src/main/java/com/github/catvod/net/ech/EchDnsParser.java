package com.github.catvod.net.ech;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Bounded DNS wire parser for HTTPS records used by the optional ECH transport. */
public final class EchDnsParser {
    private static final int HTTPS = 65;
    private static final int MAX_RECORDS = 256;
    private static final int MAX_ALIASES = 8;

    private EchDnsParser() {
    }

    public static final class Result {
        public final byte[] echConfigList;
        public final String aliasTarget;
        public final long ttlSeconds;
        /** A stable diagnostic code, never DNS payload or a hostname. Null on success. */
        public final String reason;

        private Result(byte[] echConfigList, String aliasTarget, long ttlSeconds, String reason) {
            this.echConfigList = echConfigList == null ? null : echConfigList.clone();
            this.aliasTarget = aliasTarget;
            this.ttlSeconds = ttlSeconds;
            this.reason = reason;
        }

        public boolean hasEch() {
            return echConfigList != null;
        }

        public boolean hasAlias() {
            return aliasTarget != null;
        }
    }

    /** Builds one IN/HTTPS question. The caller supplies an ASCII (possibly IDNA) name. */
    public static byte[] buildQuery(int id, String host) {
        if (id < 0 || id > 65535) throw new IllegalArgumentException("dns_id");
        String name = normalize(host);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write16(out, id);
        write16(out, 0x0100); // Recursion desired.
        write16(out, 1);
        write16(out, 0);
        write16(out, 0);
        write16(out, 0);
        for (String label : name.split("\\.")) {
            out.write(label.length());
            for (int i = 0; i < label.length(); i++) out.write(label.charAt(i));
        }
        out.write(0);
        write16(out, HTTPS);
        write16(out, 1);
        return out.toByteArray();
    }

    /**
     * Returns an ECH list, a bounded alias to query next, or a rejection reason.
     * Only answer records participate in service selection. Additional data is not trusted.
     * This adapter does not redirect the connection to a ServiceMode target or another port.
     */
    public static Result parse(byte[] response, int expectedId, String expectedHost, int port) {
        try {
            if (expectedId < 0 || expectedId > 65535 || port < 1 || port > 65535)
                throw invalid("invalid_expectation");
            String host;
            try {
                host = normalize(expectedHost);
            } catch (IllegalArgumentException e) {
                throw invalid("invalid_expectation");
            }
            if (response == null || response.length < 12 || response.length > 65535)
                throw invalid("message_size");
            Reader reader = new Reader(response);
            if (reader.u16() != expectedId) throw invalid("id_mismatch");
            int flags = reader.u16();
            if ((flags & 0x8000) == 0 || (flags & 0x7800) != 0 || (flags & 0x0040) != 0)
                throw invalid("invalid_flags");
            if ((flags & 0x0200) != 0) throw invalid("truncated");
            if ((flags & 15) != 0) throw invalid("dns_rcode");
            int questions = reader.u16();
            int answers = reader.u16();
            int authorities = reader.u16();
            int additional = reader.u16();
            if (questions != 1) throw invalid("question_count");
            if (answers + authorities + additional > MAX_RECORDS) throw invalid("record_limit");
            String question = reader.name(response.length, true);
            if (!host.equals(question) || reader.u16() != HTTPS || reader.u16() != 1)
                throw invalid("question_mismatch");
            List<Record> records = new ArrayList<>();
            boolean optSeen = false;
            for (int i = 0; i < answers + authorities + additional; i++) {
                String owner = reader.name(response.length, true);
                int type = reader.u16();
                int dnsClass = reader.u16();
                long ttl = reader.u32();
                int size = reader.u16();
                reader.require(size, response.length);
                int end = reader.position + size;
                if (type == 41) {
                    if (i < answers + authorities || optSeen || !owner.equals(".")
                            || (ttl & 0xffff0000L) != 0) throw invalid("invalid_edns");
                    optSeen = true;
                    while (reader.position < end) {
                        reader.require(4, end);
                        reader.u16();
                        int optionSize = reader.u16();
                        reader.require(optionSize, end);
                        reader.position += optionSize;
                    }
                } else if (i < answers && dnsClass == 1 && (type == 5 || type == HTTPS)) {
                    // RFC 2181: a TTL with its high bit set is treated as zero.
                    Record record = new Record(owner, ttl > 0x7fffffffL ? 0 : ttl);
                    if (type == 5) {
                        record.cname = true;
                        record.target = reader.name(end, true);
                        if (reader.position != end) throw invalid("cname_length");
                    } else {
                        reader.require(2, end);
                        record.priority = reader.u16();
                        // RFC 9460 does not permit compression in TargetName.
                        record.target = reader.name(end, false);
                        readParameters(reader, end, record, port);
                    }
                    records.add(record);
                }
                reader.position = end;
            }
            if (reader.position != response.length) throw invalid("trailing_data");
            return select(records, host);
        } catch (Invalid e) {
            return rejected(e.getMessage());
        }
    }

    private static Result select(List<Record> records, String host) throws Invalid {
        String current = host;
        long ttl = Long.MAX_VALUE;
        Set<String> visited = new HashSet<>();
        for (int hops = 0; hops <= MAX_ALIASES; hops++) {
            if (!visited.add(current)) throw invalid("alias_loop");
            List<Record> services = new ArrayList<>();
            List<Record> aliases = new ArrayList<>();
            Record cname = null;
            for (Record record : records) {
                if (!record.owner.equals(current)) continue;
                if (record.cname) {
                    if (cname != null && !cname.target.equals(record.target))
                        throw invalid("conflicting_cname");
                    cname = record;
                    ttl = Math.min(ttl, record.ttl);
                } else if (record.priority == 0) {
                    aliases.add(record);
                } else {
                    services.add(record);
                }
            }
            String next = null;
            if (cname != null) {
                if (!aliases.isEmpty() || !services.isEmpty()) throw invalid("cname_conflict");
                next = cname.target;
            } else if (!aliases.isEmpty()) {
                // AliasMode takes precedence over ServiceMode, per RFC 9460.
                for (Record alias : aliases) {
                    if (alias.reason != null) throw invalid(alias.reason);
                    if (next != null && !next.equals(alias.target)) throw invalid("conflicting_alias");
                    next = alias.target;
                    ttl = Math.min(ttl, alias.ttl);
                }
            }
            if (next != null) {
                if (next.equals(".")) throw invalid("service_unavailable");
                if (hops == MAX_ALIASES) throw invalid("alias_limit");
                current = next;
                continue;
            }
            if (services.isEmpty()) {
                if (current.equals(host)) return rejected("no_https_record");
                return new Result(null, current, ttl, null);
            }
            int priority = Integer.MAX_VALUE;
            for (Record service : services) priority = Math.min(priority, service.priority);
            byte[] config = null;
            for (Record service : services) {
                if (service.priority != priority) continue;
                if (service.reason != null) throw invalid(service.reason);
                if (!service.target.equals(".") && !service.target.equals(current))
                    throw invalid("unsupported_service_target");
                if (service.ech == null) throw invalid("no_ech");
                if (config != null && !Arrays.equals(config, service.ech))
                    throw invalid("ambiguous_service");
                config = service.ech;
                ttl = Math.min(ttl, service.ttl);
            }
            return new Result(config, null, ttl, null);
        }
        throw invalid("alias_limit");
    }

    private static void readParameters(Reader reader, int end, Record record, int port)
            throws Invalid {
        List<Integer> mandatory = new ArrayList<>();
        Set<Integer> present = new HashSet<>();
        boolean alpn = false;
        boolean usableAlpn = false;
        boolean noDefault = false;
        int previous = -1;
        while (reader.position < end) {
            reader.require(4, end);
            int key = reader.u16();
            int size = reader.u16();
            reader.require(size, end);
            int valueEnd = reader.position + size;
            if (key <= previous) throw invalid("parameter_order");
            previous = key;
            present.add(key);
            // AliasMode SvcParams must be ignored, but their wire framing must remain valid.
            if (record.priority == 0) {
                reader.position = valueEnd;
                continue;
            }
            if (key == 0) {
                if (size == 0 || (size & 1) != 0) record.reason = "invalid_mandatory";
                else {
                    int last = 0;
                    while (reader.position < valueEnd) {
                        int required = reader.u16();
                        if (required <= last) record.reason = "invalid_mandatory";
                        last = required;
                        mandatory.add(required);
                    }
                }
            } else if (key == 1) {
                alpn = true;
                Set<String> protocols = new HashSet<>();
                if (size == 0) record.reason = "invalid_alpn";
                while (reader.position < valueEnd) {
                    int length = reader.u8();
                    if (length == 0 || length > valueEnd - reader.position) {
                        record.reason = "invalid_alpn";
                        break;
                    }
                    StringBuilder protocol = new StringBuilder();
                    for (int i = 0; i < length; i++) protocol.append((char) reader.u8());
                    String name = protocol.toString();
                    if (!protocols.add(name)) record.reason = "invalid_alpn";
                    if (name.equals("h2") || name.equals("http/1.1")) usableAlpn = true;
                }
            } else if (key == 2) {
                noDefault = true;
                if (size != 0) record.reason = "invalid_no_default_alpn";
            } else if (key == 3) {
                if (size != 2) record.reason = "invalid_port";
                else if (reader.u16() != port) record.reason = "unsupported_port";
            } else if (key == 4) {
                if (size == 0 || size % 4 != 0) record.reason = "invalid_ipv4hint";
            } else if (key == 5) {
                byte[] ech = Arrays.copyOfRange(reader.bytes, reader.position, valueEnd);
                if (!validEch(ech)) record.reason = "invalid_ech";
                else record.ech = ech;
            } else if (key == 6) {
                if (size == 0 || size % 16 != 0) record.reason = "invalid_ipv6hint";
            } else if (key == 65535) {
                record.reason = "invalid_parameter_key";
            }
            reader.position = valueEnd;
        }
        if (record.priority == 0) return;
        for (int key : mandatory) {
            if (key > 6) record.reason = "unsupported_mandatory";
            else if (!present.contains(key)) record.reason = "missing_mandatory";
        }
        if (noDefault && !alpn) record.reason = "missing_alpn";
        else if (noDefault && !usableAlpn) record.reason = "unsupported_alpn";
    }

    private static boolean validEch(byte[] bytes) {
        if (bytes.length < 7 || unsigned16(bytes, 0) != bytes.length - 2) return false;
        int offset = 2;
        while (offset < bytes.length) {
            if (bytes.length - offset < 4) return false;
            int size = unsigned16(bytes, offset + 2);
            if (size == 0 || size > bytes.length - offset - 4) return false;
            offset += 4 + size;
        }
        return offset == bytes.length;
    }

    private static String normalize(String host) {
        if (host == null || host.isEmpty()) throw new IllegalArgumentException("dns_name");
        String name = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (name.isEmpty() || name.length() > 253) throw new IllegalArgumentException("dns_name");
        for (String label : name.split("\\.", -1)) {
            if (label.isEmpty() || label.length() > 63) throw new IllegalArgumentException("dns_name");
            for (int i = 0; i < label.length(); i++) {
                if (!nameCharacter(label.charAt(i))) throw new IllegalArgumentException("dns_name");
            }
        }
        return name.toLowerCase(Locale.ROOT);
    }

    private static boolean nameCharacter(int c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9'
                || c == '-' || c == '_';
    }

    private static void write16(ByteArrayOutputStream out, int value) {
        out.write(value >>> 8);
        out.write(value);
    }

    private static int unsigned16(byte[] bytes, int position) {
        return (bytes[position] & 255) << 8 | bytes[position + 1] & 255;
    }

    private static Result rejected(String reason) {
        return new Result(null, null, 0, reason);
    }

    private static Invalid invalid(String reason) {
        return new Invalid(reason);
    }

    private static final class Invalid extends Exception {
        Invalid(String reason) {
            super(reason);
        }
    }

    private static final class Record {
        final String owner;
        final long ttl;
        boolean cname;
        int priority;
        String target;
        byte[] ech;
        String reason;

        Record(String owner, long ttl) {
            this.owner = owner;
            this.ttl = ttl;
        }
    }

    private static final class Reader {
        final byte[] bytes;
        int position;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        void require(int length, int limit) throws Invalid {
            if (length < 0 || position > limit || length > limit - position)
                throw invalid("record_bounds");
        }

        int u8() throws Invalid {
            require(1, bytes.length);
            return bytes[position++] & 255;
        }

        int u16() throws Invalid {
            require(2, bytes.length);
            int value = unsigned16(bytes, position);
            position += 2;
            return value;
        }

        long u32() throws Invalid {
            return (long) u16() << 16 | u16();
        }

        String name(int limit, boolean allowCompression) throws Invalid {
            int cursor = position;
            int consumed = -1;
            int expanded = 1;
            int steps = 0;
            StringBuilder name = new StringBuilder();
            while (true) {
                if (++steps > 128 || cursor >= limit) throw invalid("name_bounds");
                int size = bytes[cursor++] & 255;
                if ((size & 0xc0) == 0xc0) {
                    if (!allowCompression) throw invalid("compressed_target");
                    if (cursor >= limit) throw invalid("name_bounds");
                    int pointer = (size & 0x3f) << 8 | bytes[cursor++] & 255;
                    if (pointer < 12 || pointer >= cursor - 2) throw invalid("name_pointer");
                    if (consumed == -1) consumed = cursor;
                    cursor = pointer;
                    limit = bytes.length;
                } else if (size == 0) {
                    position = consumed == -1 ? cursor : consumed;
                    return name.length() == 0 ? "." : name.toString().toLowerCase(Locale.ROOT);
                } else {
                    if (size > 63 || size > limit - cursor) throw invalid("name_label");
                    expanded += size + 1;
                    if (expanded > 255) throw invalid("name_length");
                    if (name.length() > 0) name.append('.');
                    for (int i = 0; i < size; i++) {
                        int c = bytes[cursor++] & 255;
                        if (!nameCharacter(c)) throw invalid("name_character");
                        name.append((char) c);
                    }
                }
            }
        }
    }
}
