package com.im.netty.common.util;

import com.im.netty.common.constants.ImConstant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/**
 * MD5 hash utilities.
 */
public final class MD5Utils {

    private MD5Utils() {}

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    /**
     * Generate a random MD5 hex string incorporating current time and a UUID.
     */
    public static String randomMD5HexString(String... params) {
        StringBuilder sb = new StringBuilder();
        sb.append(System.currentTimeMillis());
        if (params != null) {
            for (String p : params) {
                sb.append(p);
            }
        }
        sb.append(UUID.randomUUID());
        return md5(sb.toString());
    }

    /**
     * MD5 hash of a string, returning lowercase hex.
     */
    public static String md5(String src) {
        if (src == null || src.isEmpty()) return null;
        return md5(src.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * MD5 hash of a string with salt appended.
     */
    public static String md5WithSalt(String src) {
        return md5(src + ImConstant.MD5_SALT);
    }

    /**
     * MD5 hash of raw bytes.
     */
    public static String md5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(bytes);
            return bytesToHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(HEX_CHARS[(b & 0xF0) >> 4]);
            sb.append(HEX_CHARS[b & 0x0F]);
        }
        return sb.toString();
    }
}
