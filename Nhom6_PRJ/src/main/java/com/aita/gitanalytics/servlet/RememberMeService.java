package com.aita.gitanalytics.servlet;

import com.aita.gitanalytics.dao.UserDAO;
import com.aita.gitanalytics.dao.UserDAO.UserAccount;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final class RememberMeService {

    private static final String COOKIE_NAME = "AITA_REMEMBER";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_AGE_SECONDS = 30 * 24 * 60 * 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RememberMeService() {
    }

    static boolean issue(UserAccount account, HttpServletRequest request,
                         HttpServletResponse response, UserDAO userDAO) {
        revoke(request, response, userDAO);
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = toHex(bytes);
        Timestamp expiresAt = new Timestamp(System.currentTimeMillis() + MAX_AGE_SECONDS * 1000L);
        if (!userDAO.saveRememberToken(hash(token), account.userId, expiresAt)) {
            clearCookie(request, response);
            return false;
        }
        setCookie(token, MAX_AGE_SECONDS, request, response);
        return true;
    }

    static UserAccount restore(HttpServletRequest request, HttpServletResponse response, UserDAO userDAO) {
        String token = readCookie(request);
        if (token == null) {
            return null;
        }
        if (!token.matches("[0-9a-f]{64}")) {
            clearCookie(request, response);
            return null;
        }
        UserAccount account = userDAO.findRememberedUser(hash(token));
        if (account == null) {
            clearCookie(request, response);
        }
        return account;
    }

    static boolean revoke(HttpServletRequest request, HttpServletResponse response, UserDAO userDAO) {
        String token = readCookie(request);
        if (token != null && token.matches("[0-9a-f]{64}")) {
            userDAO.deleteRememberToken(hash(token));
        }
        clearCookie(request, response);
        return true;
    }

    private static String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private static void clearCookie(HttpServletRequest request, HttpServletResponse response) {
        setCookie("", 0, request, response);
    }

    private static void setCookie(String value, int maxAge, HttpServletRequest request,
                                  HttpServletResponse response) {
        String path = request.getContextPath().isEmpty() ? "/" : request.getContextPath() + "/";
        String header = COOKIE_NAME + "=" + value + "; Path=" + path + "; Max-Age=" + maxAge
                + "; HttpOnly; SameSite=Lax";
        if (request.isSecure()) {
            header += "; Secure";
        }
        response.addHeader("Set-Cookie", header);
    }

    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}