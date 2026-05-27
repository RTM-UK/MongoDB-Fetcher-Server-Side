package com.example.punishmentinfo;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class CountCommand implements CommandExecutor, TabCompleter {
    private final PunishmentInfoPlugin plugin;
    private final String permission;
    private final String actionName;
    private final Function<String, PlayerPunishmentRecord> updater;

    public CountCommand(
            PunishmentInfoPlugin plugin,
            MongoPunishmentRepository repository,
            String permission,
            String actionName,
            Function<String, PlayerPunishmentRecord> updater
    ) {
        this.plugin = plugin;
        this.permission = permission;
        this.actionName = actionName;
        this.updater = updater;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isOp() && !sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You need OP or " + permission + " to use this command.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " <name>");
            return true;
        }

        String playerName = args[0];
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                PlayerPunishmentRecord record = updater.apply(playerName);
                Bukkit.getScheduler().runTask(plugin, () -> sendRecord(sender, actionName, record));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("MongoDB update failed for " + playerName + ": " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(ChatColor.RED + "Could not update MongoDB. Check the server console."));
            }
        });

        return true;
    }

    private void sendRecord(CommandSender sender, String action, PlayerPunishmentRecord record) {
        sender.sendMessage(ChatColor.GREEN + action + " for " + ChatColor.WHITE + record.playerName() + ChatColor.GREEN + ".");
        sender.sendMessage(ChatColor.GRAY + "Mutes: " + ChatColor.WHITE + record.mutes()
                + ChatColor.GRAY + " | Bans: " + ChatColor.WHITE + record.bans()
                + ChatColor.GRAY + " | Total: " + ChatColor.WHITE + record.totalPunishments());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1 || (!sender.isOp() && !sender.hasPermission(permission))) {
            return Collections.emptyList();
        }
        return PlayerNameTabCompleter.onlinePlayerNames(args[0]);
    }
}
