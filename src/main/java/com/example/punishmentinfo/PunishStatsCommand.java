package com.example.punishmentinfo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class PunishStatsCommand implements CommandExecutor {
    private final PunishmentInfoPlugin plugin;
    private final MongoPunishmentRepository repository;
    private final String permission;

    public PunishStatsCommand(PunishmentInfoPlugin plugin, MongoPunishmentRepository repository, String permission) {
        this.plugin = plugin;
        this.repository = repository;
        this.permission = permission;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You need OP or " + permission + " to use this command.");
            return true;
        }

        if (args.length != 0) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label);
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PunishmentStats stats = repository.getStats();
                Bukkit.getScheduler().runTask(plugin, () -> sendStats(sender, stats));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("MongoDB stats lookup failed: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Could not load MongoDB stats. Check the server console."));
            }
        });

        return true;
    }

    private void sendStats(CommandSender sender, PunishmentStats stats) {
        sender.sendMessage(ChatColor.GOLD + "MongoDB Punishment Stats:");
        sender.sendMessage(ChatColor.GRAY + "Tracked players: " + ChatColor.WHITE + stats.trackedPlayers());
        sender.sendMessage(ChatColor.GRAY + "Total mutes: " + ChatColor.WHITE + stats.totalMutes()
                + ChatColor.GRAY + " | Total bans: " + ChatColor.WHITE + stats.totalBans());
        sender.sendMessage(ChatColor.GRAY + "Most punished: " + ChatColor.WHITE + stats.mostPunishedPlayer()
                + ChatColor.GRAY + " (" + ChatColor.WHITE + stats.mostPunishedTotal() + ChatColor.GRAY + ")");
    }
}
