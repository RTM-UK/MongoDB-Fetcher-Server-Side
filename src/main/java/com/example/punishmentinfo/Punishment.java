package com.example.punishmentinfo;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public record Punishment(
        String type,
        String reason,
        String staff,
        String createdAt,
        String expiresAt,
        String status
) {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm z")
            .withLocale(Locale.UK)
            .withZone(ZoneId.systemDefault());

    public static Punishment fromDocument(Document document) {
        if (containsAnyKey(document, List.of("mutes", "mute", "bans", "ban"))) {
            String mutes = getString(document, List.of("mutes", "mute"), "0");
            String bans = getString(document, List.of("bans", "ban"), "0");

            return new Punishment(
                    "History",
                    "Mutes: " + mutes + " | Bans: " + bans,
                    "Database",
                    getDateLike(document, List.of("createdAt", "date", "timestamp", "created", "_id"), "Unknown"),
                    "N/A",
                    "Recorded"
            );
        }

        String customFields = summarizeCustomFields(document);
        if (!customFields.isBlank()) {
            return new Punishment(
                    "History",
                    customFields,
                    "Database",
                    getDateLike(document, List.of("createdAt", "date", "timestamp", "created", "_id"), "Unknown"),
                    "N/A",
                    "Recorded"
            );
        }

        boolean active = getBoolean(document, List.of("active", "isActive"), false);

        return new Punishment(
                getString(document, List.of("type", "punishmentType", "action"), "unknown"),
                getString(document, List.of("reason", "message"), "No reason given"),
                getString(document, List.of("staff", "punishedBy", "moderator", "executor"), "Unknown"),
                getDateLike(document, List.of("createdAt", "date", "timestamp", "created", "_id"), "Unknown"),
                getDateLike(document, List.of("expiresAt", "expiry", "expires", "endsAt", "duration"), "Never"),
                active ? "Active" : "Past"
        );
    }

    public boolean isSimpleCountHistory() {
        return type.equalsIgnoreCase("History")
                && reason.toLowerCase(Locale.ROOT).contains("mute")
                && reason.toLowerCase(Locale.ROOT).contains("ban");
    }

    private static String getString(Document document, List<String> keys, String fallback) {
        for (String key : keys) {
            Object value = getFlexible(document, key);
            if (value != null && !value.toString().isBlank()) {
                return value.toString();
            }
        }
        return fallback;
    }

    private static boolean getBoolean(Document document, List<String> keys, boolean fallback) {
        for (String key : keys) {
            Object value = getFlexible(document, key);
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof String stringValue) {
                return Boolean.parseBoolean(stringValue);
            }
        }
        return fallback;
    }

    private static String getDateLike(Document document, List<String> keys, String fallback) {
        for (String key : keys) {
            Object value = getFlexible(document, key);
            if (value == null) {
                continue;
            }
            if (value instanceof Date date) {
                return DATE_FORMAT.format(date.toInstant());
            }
            if (value instanceof Instant instant) {
                return DATE_FORMAT.format(instant);
            }
            if (value instanceof Number number) {
                return DATE_FORMAT.format(Instant.ofEpochMilli(number.longValue()));
            }
            if (value instanceof ObjectId objectId) {
                return DATE_FORMAT.format(objectId.getDate().toInstant());
            }
            if (!value.toString().isBlank()) {
                return value.toString();
            }
        }
        return fallback;
    }

    private static boolean containsAnyKey(Document document, List<String> keys) {
        for (String key : keys) {
            if (getFlexible(document, key) != null) {
                return true;
            }
        }
        return false;
    }

    private static Object getFlexible(Document document, String wantedKey) {
        if (document.containsKey(wantedKey)) {
            return document.get(wantedKey);
        }

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

    private static String summarizeCustomFields(Document document) {
        List<String> fields = document.entrySet().stream()
                .filter(entry -> !isHiddenField(entry.getKey()))
                .map(entry -> entry.getKey().trim() + ": " + entry.getValue())
                .toList();

        return String.join(" | ", fields);
    }

    private static boolean isHiddenField(String key) {
        String normalizedKey = normalizeKey(key);
        return normalizedKey.equals("id")
                || normalizedKey.equals("ign")
                || normalizedKey.equals("player")
                || normalizedKey.equals("playername")
                || normalizedKey.equals("name")
                || normalizedKey.equals("username");
    }
}
