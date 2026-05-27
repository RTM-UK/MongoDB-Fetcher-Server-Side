package com.example.punishmentinfo;

import org.bson.Document;

import java.util.Locale;

public record PlayerPunishmentRecord(
        String playerName,
        int mutes,
        int bans,
        String tier,
        String region
) {
    public static PlayerPunishmentRecord fromDocument(String fallbackName, Document document) {
        return new PlayerPunishmentRecord(
                getString(document, "ign", fallbackName),
                getInt(document, "mutes"),
                getInt(document, "bans"),
                getString(document, "tier", "N/A"),
                getString(document, "region", "N/A")
        );
    }

    public int totalPunishments() {
        return mutes + bans;
    }

    private static String getString(Document document, String key, String fallback) {
        Object value = getFlexible(document, key);
        return value == null || value.toString().isBlank() ? fallback : value.toString();
    }

    private static int getInt(Document document, String key) {
        Object value = getFlexible(document, key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Integer.parseInt(stringValue.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Object getFlexible(Document document, String wantedKey) {
        String normalizedWantedKey = singularKey(normalizeKey(wantedKey));
        for (String actualKey : document.keySet()) {
            String normalizedActualKey = singularKey(normalizeKey(actualKey));
            if (normalizedActualKey.equals(normalizedWantedKey)
                    || normalizedActualKey.contains(normalizedWantedKey)
                    || normalizedWantedKey.contains(normalizedActualKey)) {
                return document.get(actualKey);
            }
        }
        return null;
    }

    private static String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String singularKey(String key) {
        return key.endsWith("s") ? key.substring(0, key.length() - 1) : key;
    }
}
