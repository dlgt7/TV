package com.fongmi.android.tv.player.subtitle;

import androidx.annotation.Nullable;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Minimal, bounds-checked OpenType/TrueType name-table reader. */
final class FontFamilyParser {

    private static final int TAG_TTCF = 0x74746366;
    private static final int TAG_NAME = 0x6E616D65;
    private static final int MAX_FONT_BYTES = 32 * 1024 * 1024;

    private FontFamilyParser() {
    }

    @Nullable
    static String read(File file) {
        if (file == null || !file.isFile() || file.length() <= 0 || file.length() > MAX_FONT_BYTES) return null;
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
            if (data.length < 12) return null;
            if (buffer.getInt(0) == TAG_TTCF) return readCollection(buffer);
            return readFace(buffer, 0);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Nullable
    private static String readCollection(ByteBuffer buffer) {
        if (!contains(buffer, 0, 12)) return null;
        long count = uint(buffer, 8);
        if (count <= 0 || count > 256 || !contains(buffer, 12, count * 4)) return null;
        for (int i = 0; i < count; i++) {
            long offset = uint(buffer, 12 + i * 4);
            if (offset > Integer.MAX_VALUE) continue;
            String family = readFace(buffer, (int) offset);
            if (family != null) return family;
        }
        return null;
    }

    @Nullable
    private static String readFace(ByteBuffer buffer, int faceOffset) {
        if (!contains(buffer, faceOffset, 12)) return null;
        int tableCount = ushort(buffer, faceOffset + 4);
        if (tableCount <= 0 || tableCount > 4096 || !contains(buffer, faceOffset + 12L, tableCount * 16L)) return null;
        for (int i = 0; i < tableCount; i++) {
            int record = faceOffset + 12 + i * 16;
            if (buffer.getInt(record) != TAG_NAME) continue;
            long offset = uint(buffer, record + 8);
            long length = uint(buffer, record + 12);
            if (offset > Integer.MAX_VALUE || length > Integer.MAX_VALUE || !contains(buffer, offset, length)) return null;
            return readNameTable(buffer, (int) offset, (int) length);
        }
        return null;
    }

    @Nullable
    private static String readNameTable(ByteBuffer buffer, int tableOffset, int tableLength) {
        if (tableLength < 6 || !contains(buffer, tableOffset, tableLength)) return null;
        int count = ushort(buffer, tableOffset + 2);
        int strings = ushort(buffer, tableOffset + 4);
        if (count <= 0 || count > 4096 || 6L + count * 12L > tableLength) return null;
        Candidate best = null;
        for (int i = 0; i < count; i++) {
            int record = tableOffset + 6 + i * 12;
            int platform = ushort(buffer, record);
            int encoding = ushort(buffer, record + 2);
            int language = ushort(buffer, record + 4);
            int nameId = ushort(buffer, record + 6);
            int length = ushort(buffer, record + 8);
            int offset = ushort(buffer, record + 10);
            if (nameId != 16 && nameId != 1) continue;
            long absolute = (long) tableOffset + strings + offset;
            if (absolute < tableOffset || absolute + length > (long) tableOffset + tableLength || !contains(buffer, absolute, length)) continue;
            String value = decode(buffer, (int) absolute, length, platform, encoding);
            if (value == null) continue;
            Candidate current = new Candidate(value, score(nameId, platform, language));
            if (best == null || current.score > best.score) best = current;
        }
        return best == null ? null : best.value;
    }

    @Nullable
    private static String decode(ByteBuffer buffer, int offset, int length, int platform, int encoding) {
        if (length <= 0) return null;
        try {
            byte[] value = new byte[length];
            ByteBuffer copy = buffer.duplicate();
            copy.position(offset);
            copy.get(value);
            Charset charset = platform == 0 || platform == 3
                    ? StandardCharsets.UTF_16BE
                    : platform == 1 ? Charset.forName("x-MacRoman") : StandardCharsets.ISO_8859_1;
            String text = new String(value, charset).replace("\u0000", "").trim();
            return text.isEmpty() ? null : text;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int score(int nameId, int platform, int language) {
        int score = nameId == 16 ? 100 : 0;
        if (platform == 0 || platform == 3) score += 20;
        if (language == 0x0409 || language == 0) score += 5;
        return score;
    }

    private static int ushort(ByteBuffer buffer, int offset) {
        return Short.toUnsignedInt(buffer.getShort(offset));
    }

    private static long uint(ByteBuffer buffer, int offset) {
        return Integer.toUnsignedLong(buffer.getInt(offset));
    }

    private static boolean contains(ByteBuffer buffer, long offset, long length) {
        return offset >= 0 && length >= 0 && offset <= buffer.limit() && length <= buffer.limit() - offset;
    }

    private record Candidate(String value, int score) {
    }
}
