package com.aita.gitanalytics.dao;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class PasswordHasher {

    private static final String PREFIX = "pbkdf2_sha256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public static String hash(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password must not be null.");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        byte[] derived = derive(password, salt, ITERATIONS);
        return PREFIX + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    public static boolean verify(String password, String storedValue) {
        if (password == null || storedValue == null) {
            return false;
        }
        if (!storedValue.startsWith(PREFIX + "$")) {
            return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8),
                    storedValue.getBytes(StandardCharsets.UTF_8));
        }

        String[] parts = storedValue.split("\\$", -1);
        if (parts.length != 4) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations < 10_000 || iterations > 1_000_000
                    || salt.length < SALT_LENGTH_BYTES || expected.length != KEY_LENGTH_BITS / 8) {
                return false;
            }
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean needsUpgrade(String storedValue) {
        return storedValue == null || !storedValue.startsWith(PREFIX + "$");
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is unavailable.", e);
        } finally {
            spec.clearPassword();
        }
    }
}