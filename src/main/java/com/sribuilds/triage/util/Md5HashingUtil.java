package com.sribuilds.triage.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic MD5 hashing for stack-trace deduplication.
 *
 * <p>The exact same stack trace always produces the exact same hash, which is
 * used as the Redis deduplication key. MD5 is intentionally used here (not a
 * stronger digest) because the hash is a <em>cache key</em>, not a security
 * primitive.</p>
 */
public final class Md5HashingUtil {

    private Md5HashingUtil() {
    }

    /**
     * Produces the hex-encoded MD5 digest (32 lowercase chars) of the input.
     *
     * @param value raw input, typically a stack trace / error log
     * @return 32-character lowercase hex MD5 digest
     * @throws IllegalArgumentException if the value is blank
     */
    public static String hash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Cannot hash a blank value");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            // MD5 is guaranteed to be present on every JDK
            throw new IllegalStateException("MD5 algorithm not available", e);
        }
    }
}