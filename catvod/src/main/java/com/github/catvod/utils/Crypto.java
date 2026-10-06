package com.github.catvod.utils;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

public final class Crypto {
    private static final String MD5 = "MD5";
    private static final String SHA_256 = "SHA-256";
    private static final int BUFFER_SIZE = 65536;
    private static final int GCM_TAG_SIZE = 16;
    private static final int AES_BLOCK_SIZE = 16;
    private static final int DES_BLOCK_SIZE = 8;
    private static final int DES_EDE_TWO_KEY_SIZE = 16;
    private static final int DES_EDE_THREE_KEY_SIZE = 24;
    private static final char[] HEX = "0123456789abcdef".toCharArray();
    private static final OAEPParameterSpec OAEP_SHA1_PARAMETERS = new OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);

    public static String md5(String value) {
        return Crypto.hash(value, MD5);
    }

    public static String md5(File file) {
        try {
            return Crypto.toHex(Crypto.digest(file, Crypto.newDigest(MD5)));
        }
        catch (IOException e) {
            return "";
        }
    }

    public static boolean equals(File file, String expected) {
        return expected != null && !expected.isEmpty() && expected.equalsIgnoreCase(Crypto.md5(file));
    }

    public static byte[] sha256(File file) throws IOException {
        return Crypto.digest(file, Crypto.newDigest(SHA_256));
    }

    public static String sha256(String value) {
        return Crypto.hash(value, SHA_256);
    }

    public static String randomUrlSafe(int size) {
        if (size <= 0 || size > 1024) {
            return "";
        }
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return Base64.encodeToString((byte[])bytes, (int)11);
    }

    public static MessageDigest newDigest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] decryptAesCbc(byte[] data, byte[] key, byte[] iv) throws GeneralSecurityException {
        return Crypto.newAesCipher("AES/CBC/PKCS5Padding", false, key, iv).doFinal(data);
    }

    public static String aes(String mode, boolean encrypt, String input, boolean inputBase64, String key, String iv, boolean outputBase64) {
        try {
            if (mode.contains("GCM")) {
                return Crypto.aesGcm(encrypt, input, inputBase64, key, iv, outputBase64);
            }
            byte[] keyBytes = Crypto.padParameter(key.getBytes(StandardCharsets.UTF_8), 16);
            byte[] ivBytes = Crypto.getIv(iv, 16);
            Cipher cipher = Crypto.newAesCipher(Crypto.getAesTransformation(mode), encrypt, keyBytes, ivBytes);
            return Crypto.encode(cipher.doFinal(Crypto.decode(input, inputBase64)), outputBase64);
        }
        catch (Exception e) {
            return "";
        }
    }

    public static String des(String mode, boolean encrypt, String input, boolean inputBase64, String key, String iv, boolean outputBase64) {
        try {
            byte[] keyBytes = Crypto.getDesEdeKey(key);
            byte[] ivBytes = Crypto.getIv(iv, 8);
            Cipher cipher = Crypto.newCipher(Crypto.getDesTransformation(mode), "DESede", encrypt, keyBytes, ivBytes);
            return Crypto.encode(cipher.doFinal(Crypto.decode(input, inputBase64)), outputBase64);
        }
        catch (Exception e) {
            return "";
        }
    }

    public static String rsa(String mode, boolean publicKey, boolean encrypt, String input, boolean inputBase64, String key, boolean outputBase64) {
        try {
            Key rsaKey = Crypto.generateRsaKey(publicKey, key);
            RsaMode rsaMode = RsaMode.from(mode);
            Cipher cipher = Crypto.newRsaCipher(rsaMode, encrypt, rsaKey);
            byte[] output = Crypto.transformRsa(cipher, rsaMode, encrypt, rsaKey, Crypto.decode(input, inputBase64));
            return Crypto.encode(output, outputBase64);
        }
        catch (Exception e) {
            return "";
        }
    }

    private static byte[] digest(File file, MessageDigest digest) throws IOException {
        try (FileInputStream input = new FileInputStream(file);){
            int count;
            byte[] buffer = new byte[65536];
            while ((count = ((InputStream)input).read(buffer)) != -1) {
                digest.update(buffer, 0, count);
            }
            byte[] byArray = digest.digest();
            return byArray;
        }
    }

    private static String hash(String value, String algorithm) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Crypto.toHex(Crypto.newDigest(algorithm).digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static Cipher newAesCipher(String transformation, boolean encrypt, byte[] key, byte[] iv) throws GeneralSecurityException {
        return Crypto.newCipher(transformation, "AES", encrypt, key, iv);
    }

    private static String aesGcm(boolean encrypt, String input, boolean inputBase64, String keyHex, String ivHex, boolean outputBase64) throws GeneralSecurityException {
        byte[] data;
        if (encrypt) {
            data = Crypto.decode(input, inputBase64);
        } else {
            byte[] byArray = data = inputBase64 ? Crypto.decode(input, true) : Crypto.fromHex(input);
        }
        if (!encrypt && data.length < 16) {
            return "";
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec key = new SecretKeySpec(Crypto.fromHex(keyHex), "AES");
        GCMParameterSpec params = new GCMParameterSpec(128, Crypto.fromHex(ivHex));
        cipher.init(encrypt ? 1 : 2, (Key)key, params);
        byte[] output = cipher.doFinal(data);
        return encrypt && !outputBase64 ? Crypto.toHex(output) : Crypto.encode(output, outputBase64);
    }

    private static Cipher newCipher(String transformation, String algorithm, boolean encrypt, byte[] key, byte[] iv) throws GeneralSecurityException {
        int operation;
        if (iv == null && transformation.contains("/CBC/")) {
            throw new GeneralSecurityException("IV is required for CBC mode");
        }
        Cipher cipher = Cipher.getInstance(transformation);
        SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
        int n = operation = encrypt ? 1 : 2;
        if (iv == null) {
            cipher.init(operation, keySpec);
        } else {
            cipher.init(operation, (Key)keySpec, new IvParameterSpec(iv));
        }
        return cipher;
    }

    private static byte[] getIv(String iv, int blockSize) {
        if (iv == null || iv.isEmpty()) {
            return null;
        }
        return Crypto.padParameter(iv.getBytes(StandardCharsets.UTF_8), blockSize);
    }

    private static String getAesTransformation(String mode) {
        if (mode.startsWith("AES/CBC")) {
            return "AES/CBC/PKCS5Padding";
        }
        if (mode.startsWith("AES/ECB")) {
            return "AES/ECB/PKCS5Padding";
        }
        return mode + "Padding";
    }

    private static String getDesTransformation(String mode) {
        return mode.startsWith("DESede/CBC") ? "DESede/CBC/PKCS5Padding" : mode + "Padding";
    }

    private static byte[] getDesEdeKey(String key) {
        byte[] bytes = Crypto.padParameter(key.getBytes(StandardCharsets.UTF_8), 16);
        if (bytes.length != 16) {
            return bytes;
        }
        byte[] expanded = Arrays.copyOf(bytes, 24);
        System.arraycopy(bytes, 0, expanded, 16, 8);
        return expanded;
    }

    private static byte[] padParameter(byte[] value, int minLength) {
        return value.length < minLength ? Arrays.copyOf(value, minLength) : value;
    }

    private static Key generateRsaKey(boolean publicKey, String value) throws GeneralSecurityException {
        String begin = publicKey ? "-----BEGIN PUBLIC KEY-----" : "-----BEGIN PRIVATE KEY-----";
        String end = publicKey ? "-----END PUBLIC KEY-----" : "-----END PRIVATE KEY-----";
        String key = value.replace("\r", "").replace("\n", "").replace(begin, "").replace(end, "");
        KeyFactory factory = KeyFactory.getInstance("RSA");
        byte[] bytes = Base64.decode((String)key, (int)0);
        return publicKey ? factory.generatePublic(new X509EncodedKeySpec(bytes)) : factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
    }

    private static Cipher newRsaCipher(RsaMode mode, boolean encrypt, Key key) throws GeneralSecurityException {
        int operation;
        Cipher cipher = Cipher.getInstance(mode.transformation);
        int n = operation = encrypt ? 1 : 2;
        if (mode == RsaMode.OAEP_SHA1) {
            cipher.init(operation, key, OAEP_SHA1_PARAMETERS);
        } else {
            cipher.init(operation, key);
        }
        return cipher;
    }

    private static byte[] transformRsa(Cipher cipher, RsaMode mode, boolean encrypt, Key key, byte[] input) throws GeneralSecurityException {
        if (input.length == 0) {
            return input;
        }
        int rsaBlockSize = Crypto.getRsaBlockSize(key);
        int inputBlockSize = encrypt ? rsaBlockSize - mode.paddingOverhead : rsaBlockSize;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (int offset = 0; offset < input.length; offset += inputBlockSize) {
            int length = Math.min(inputBlockSize, input.length - offset);
            byte[] block = Crypto.transformRsaBlock(cipher, mode, input, offset, length, rsaBlockSize);
            output.write(block, 0, block.length);
        }
        return output.toByteArray();
    }

    private static byte[] transformRsaBlock(Cipher cipher, RsaMode mode, byte[] input, int offset, int length, int blockSize) throws GeneralSecurityException {
        if (mode != RsaMode.NO_PADDING || length == blockSize) {
            return cipher.doFinal(input, offset, length);
        }
        byte[] padded = new byte[blockSize];
        System.arraycopy(input, offset, padded, blockSize - length, length);
        return cipher.doFinal(padded);
    }

    private static int getRsaBlockSize(Key key) throws GeneralSecurityException {
        if (!(key instanceof RSAKey)) {
            throw new GeneralSecurityException("Invalid RSA key");
        }
        RSAKey rsaKey = (RSAKey)((Object)key);
        return (rsaKey.getModulus().bitLength() + 7) / 8;
    }

    private static byte[] decode(String input, boolean base64) {
        return base64 ? Base64.decode((String)input.replace('_', '/').replace('-', '+'), (int)0) : input.getBytes(StandardCharsets.UTF_8);
    }

    private static String encode(byte[] output, boolean base64) {
        return base64 ? Base64.encodeToString((byte[])output, (int)2) : new String(output, StandardCharsets.UTF_8);
    }

    private static String toHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; ++index) {
            int value = bytes[index] & 0xFF;
            result[index * 2] = HEX[value >>> 4];
            result[index * 2 + 1] = HEX[value & 0xF];
        }
        return new String(result);
    }

    private static byte[] fromHex(String value) {
        if (value == null || (value.length() & 1) != 0) {
            throw new IllegalArgumentException("Invalid hex");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; ++i) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hex");
            }
            result[i] = (byte)(high << 4 | low);
        }
        return result;
    }

    private static enum RsaMode {
        PKCS1("RSA/ECB/PKCS1Padding", 11),
        NO_PADDING("RSA/ECB/NoPadding", 0),
        OAEP_SHA1("RSA/ECB/OAEPWithSHA-1AndMGF1Padding", 42);

        private final String transformation;
        private final int paddingOverhead;

        private RsaMode(String transformation, int paddingOverhead) {
            this.transformation = transformation;
            this.paddingOverhead = paddingOverhead;
        }

        private static RsaMode from(String mode) throws GeneralSecurityException {
            return switch (mode) {
                case "RSA/PKCS1" -> PKCS1;
                case "RSA/None/NoPadding" -> NO_PADDING;
                case "RSA/None/OAEPPadding" -> OAEP_SHA1;
                default -> throw new GeneralSecurityException("Unsupported RSA mode: " + mode);
            };
        }
    }
}
