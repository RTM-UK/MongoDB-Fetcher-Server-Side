package com.example.punishmentinfo;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public final class MongoPunishmentRepository implements AutoCloseable {
    private final MongoClient client;
    private final MongoCollection<Document> collection;
    private final String playerNameField;
    private final int maxResults;

    private MongoPunishmentRepository(
            MongoClient client,
            MongoCollection<Document> collection,
            String playerNameField,
            int maxResults
    ) {
        this.client = client;
        this.collection = collection;
        this.playerNameField = playerNameField;
        this.maxResults = maxResults;
    }

    public static MongoPunishmentRepository fromConfig(FileConfiguration config) {
        String uri = config.getString("mongo.uri", "mongodb://localhost:27017");
        String database = config.getString("mongo.database", "minecraft");
        String collectionName = config.getString("mongo.collection", "punishments");
        String playerNameField = config.getString("query.player-name-field", "playerName");
        int maxResults = Math.max(1, config.getInt("display.max-results", 10));

        MongoClient client = MongoClients.create(uri);
        MongoCollection<Document> collection = client.getDatabase(database).getCollection(collectionName);
        return new MongoPunishmentRepository(client, collection, playerNameField, maxResults);
    }

    public List<Punishment> findByPlayerName(String playerName) {
        Pattern exactName = Pattern.compile("^" + Pattern.quote(playerName) + "$", Pattern.CASE_INSENSITIVE);

        FindIterable<Document> documents = collection.find(Filters.regex(playerNameField, exactName))
                .sort(Sorts.descending("createdAt", "date", "timestamp", "_id"))
                .limit(maxResults);

        List<Punishment> punishments = new ArrayList<>();
        for (Document document : documents) {
            punishments.add(Punishment.fromDocument(document));
        }

        punishments.sort(Comparator.comparing(Punishment::isSimpleCountHistory).reversed());
        return punishments;
    }

    public PlayerPunishmentRecord addMute(String playerName) {
        return incrementCount(playerName, "mutes");
    }

    public PlayerPunishmentRecord addBan(String playerName) {
        return incrementCount(playerName, "bans");
    }

    public PlayerPunishmentRecord resetPunishments(String playerName) {
        Bson filter = playerFilter(playerName);
        Document existing = findBestDocument(playerName).orElse(new Document(playerNameField, playerName));
        String mutesKey = findActualKey(existing, "mutes").orElse("mutes");
        String bansKey = findActualKey(existing, "bans").orElse("bans");

        collection.updateOne(
                filter,
                Updates.combine(
                        Updates.setOnInsert(playerNameField, playerName),
                        Updates.set(mutesKey, "0"),
                        Updates.set(bansKey, "0")
                ),
                new UpdateOptions().upsert(true)
        );

        return getRecord(playerName);
    }

    public PlayerPunishmentRecord getRecord(String playerName) {
        Document document = findBestDocument(playerName).orElse(new Document(playerNameField, playerName));
        return PlayerPunishmentRecord.fromDocument(playerName, document);
    }

    public PunishmentStats getStats() {
        long trackedPlayers = collection.countDocuments();
        int totalMutes = 0;
        int totalBans = 0;
        String mostPunished = "N/A";
        int mostPunishedTotal = -1;

        for (Document document : collection.find()) {
            String playerName = Optional.ofNullable(getFlexible(document, playerNameField))
                    .map(Object::toString)
                    .orElse("Unknown");
            int mutes = readInt(document, "mutes");
            int bans = readInt(document, "bans");
            int total = mutes + bans;

            totalMutes += mutes;
            totalBans += bans;

            if (total > mostPunishedTotal) {
                mostPunished = playerName;
                mostPunishedTotal = total;
            }
        }

        return new PunishmentStats(trackedPlayers, totalMutes, totalBans, mostPunished, Math.max(0, mostPunishedTotal));
    }

    private PlayerPunishmentRecord incrementCount(String playerName, String fieldName) {
        Bson filter = playerFilter(playerName);
        Document existing = findBestDocument(playerName).orElse(new Document(playerNameField, playerName));
        String actualKey = findActualKey(existing, fieldName).orElse(fieldName);
        int nextValue = readInt(existing, fieldName) + 1;

        collection.updateOne(
                filter,
                Updates.combine(
                        Updates.setOnInsert(playerNameField, playerName),
                        Updates.set(actualKey, String.valueOf(nextValue))
                ),
                new UpdateOptions().upsert(true)
        );

        return getRecord(playerName);
    }

    private Optional<Document> findBestDocument(String playerName) {
        List<Document> documents = new ArrayList<>();
        for (Document document : collection.find(playerFilter(playerName))) {
            documents.add(document);
        }

        return documents.stream()
                .sorted(Comparator.comparing((Document document) -> hasCountFields(document)).reversed())
                .findFirst();
    }

    private Bson playerFilter(String playerName) {
        Pattern exactName = Pattern.compile("^" + Pattern.quote(playerName) + "$", Pattern.CASE_INSENSITIVE);
        return Filters.regex(playerNameField, exactName);
    }

    private boolean hasCountFields(Document document) {
        return getFlexible(document, "mutes") != null || getFlexible(document, "bans") != null;
    }

    private int readInt(Document document, String key) {
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

    private Optional<String> findActualKey(Document document, String wantedKey) {
        String normalizedWantedKey = singularKey(normalizeKey(wantedKey));
        return document.keySet().stream()
                .filter(actualKey -> singularKey(normalizeKey(actualKey)).equals(normalizedWantedKey))
                .findFirst();
    }

    private Object getFlexible(Document document, String wantedKey) {
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

    private String normalizeKey(String key) {
        return key == null ? "" : key.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String singularKey(String key) {
        return key.endsWith("s") ? key.substring(0, key.length() - 1) : key;
    }

    @Override
    public void close() {
        client.close();
    }
}
