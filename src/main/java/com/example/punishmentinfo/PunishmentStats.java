package com.example.punishmentinfo;

public record PunishmentStats(
        long trackedPlayers,
        int totalMutes,
        int totalBans,
        String mostPunishedPlayer,
        int mostPunishedTotal
) {
}
