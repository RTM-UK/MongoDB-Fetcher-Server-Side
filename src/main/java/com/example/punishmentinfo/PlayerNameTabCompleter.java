package com.example.punishmentinfo;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlayerNameTabCompleter {
    private PlayerNameTabCompleter() {
    }

    public static List<String> onlinePlayerNames(String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lowerPrefix)) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
