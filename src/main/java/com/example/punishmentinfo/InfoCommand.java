package com.example.punishmentinfo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class InfoCommand implements CommandExecutor, TabCompleter {
    private final PunishmentInfoPlugin plugin;
    private final MongoPunishmentRepository repository;

    public InfoCommand(PunishmentInfoPlugin plugin, MongoPunishmentRepository repository) {
        this.plugin = plugin;
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("punishmentinfo.info")) {
            sender.sendMessage(ChatColor.RED + "You need OP or punishmentinfo.info to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <name>");
            return true;
        }

        String playerName = args[0];
        sender.sendMessage(ChatColor.GRAY + "Looking up punishments for " + ChatColor.WHITE + playerName + ChatColor.GRAY + "...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<Punishment> punishments = repository.findByPlayerName(playerName);
                Bukkit.getScheduler().runTask(plugin, () -> sendResults(sender, playerName, punishments));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("MongoDB lookup failed for " + playerName + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Could not load punishments. Check the server console."));
            }
        });

        return true;
    }

    private void sendResults(CommandSender sender, String playerName, List<Punishment> punishments) {
        if (punishments.isEmpty()) {
            sender.sendMessage(ChatColor.GREEN + "No punishments found for " + playerName + ".");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "Punishments for " + ChatColor.WHITE + playerName + ChatColor.GOLD + ":");

        for (int index = 0; index < punishments.size(); index++) {
            Punishment punishment = punishments.get(index);
            if (punishment.type().equalsIgnoreCase("History")) {
                sender.sendMessage(ChatColor.YELLOW + "" + (index + 1) + ". "
                        + ChatColor.WHITE + punishment.reason());
                sender.sendMessage(ChatColor.GRAY + "   Status: " + ChatColor.WHITE + punishment.status()
                        + ChatColor.GRAY + " | Last updated: " + ChatColor.WHITE + punishment.createdAt());
                continue;
            }

            sender.sendMessage(ChatColor.YELLOW + "" + (index + 1) + ". "
                    + ChatColor.RED + punishment.type()
                    + ChatColor.GRAY + " | " + ChatColor.WHITE + punishment.reason());
            sender.sendMessage(ChatColor.GRAY + "   Staff: " + ChatColor.WHITE + punishment.staff()
                    + ChatColor.GRAY + " | Status: " + ChatColor.WHITE + punishment.status());
            sender.sendMessage(ChatColor.GRAY + "   Date: " + ChatColor.WHITE + punishment.createdAt()
                    + ChatColor.GRAY + " | Expires: " + ChatColor.WHITE + punishment.expiresAt());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || (!sender.isOp() && !sender.hasPermission("punishmentinfo.info"))) {
            return Collections.emptyList();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(player.getName());
            }
        }
        return names;
    }
}
