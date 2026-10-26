package com.aita.gitanalytics.util;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tiện ích băm mã hóa SHA-256 phục vụ xác thực tính toàn vẹn của Artifact nộp bài (Section 4.4.2)
 * và kiểm tra chuẩn Barem MVC2/Clean Code (40%).
 */
public final class Sha256ChecksumUtil {

    private Sha256ChecksumUtil() {}

    /**
     * Băm mảng byte sang chuỗi Hex SHA-256 (64 ký tự thường)
     */
    public static String hash(byte[] data) {
        if (data == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Băm chuỗi text sang SHA-256
     */
    public static String hashText(String text) {
        if (text == null) {
            return "";
        }
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Băm luồng InputStream sang SHA-256
     */
    public static String hashStream(InputStream is) {
        if (is == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return bytesToHex(digest.digest());
        } catch (Exception e) {
            throw new RuntimeException("Error calculating SHA-256 checksum", e);
        }
    }

    /**
     * Xác thực xem chuỗi SHA-256 thực tế có khớp với checksum mong đợi hay không
     */
    public static boolean verify(byte[] data, String expectedChecksum) {
        if (data == null || expectedChecksum == null) {
            return false;
        }
        String calculated = hash(data);
        return calculated.equalsIgnoreCase(expectedChecksum.trim());
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
