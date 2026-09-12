package com.example.ubi.analytics.ask;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Cache identity: normalized question + catalogVersion (+ optional locale).
 */
public final class AskCacheKey {

    private AskCacheKey() {
    }

    public static String normalizeQuestion(String question) {
        if (question == null) {
            return "";
        }
        return question.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public static String normalizeLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return "";
        }
        return locale.trim().toLowerCase(Locale.ROOT);
    }

    public static String material(String question, String catalogVersion, String locale) {
        String version = catalogVersion == null ? "" : catalogVersion;
        return version + "|" + normalizeLocale(locale) + "|" + normalizeQuestion(question);
    }

    public static String hash(String question, String catalogVersion, String locale) {
        String raw = material(question, catalogVersion, locale);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
