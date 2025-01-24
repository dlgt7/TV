package com.github.kiulian.downloader.parser;

import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class ProtoBuilder {

    private final ByteArrayOutputStream byteBuffer;

    public ProtoBuilder() {
        this.byteBuffer = new ByteArrayOutputStream();
    }

    public byte[] toBytes() {
        return byteBuffer.toByteArray();
    }

    public String toUrlencodedBase64() {
        return URLEncoder.encode(Base64.encodeToString(toBytes(), Base64.URL_SAFE));
    }

    private void writeVarint(long val) {
        try {
            if (val == 0) {
                byteBuffer.write(new byte[]{(byte) 0});
            } else {
                long v = val;
                while (v != 0) {
                    byte b = (byte) (v & 0x7f);
                    v >>= 7;
                    if (v != 0) {
                        b |= (byte) 0x80;
                    }
                    byteBuffer.write(new byte[]{b});
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void field(int field, byte wire) {
        long fbits = ((long) field) << 3;
        long wbits = ((long) wire) & 0x07;
        long val = fbits | wbits;
        writeVarint(val);
    }

    public void varint(int field, long val) {
        field(field, (byte) 0);
        writeVarint(val);
    }

    public void string(int field, String string) {
        byte[] strBts = string.getBytes(StandardCharsets.UTF_8);
        bytes(field, strBts);
    }

    public void bytes(int field, byte[] bytes) {
        field(field, (byte) 2);
        writeVarint(bytes.length);
        try {
            byteBuffer.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
