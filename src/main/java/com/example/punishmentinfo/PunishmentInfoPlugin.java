package com.example.punishmentinfo;

import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

public final class PunishmentInfoPlugin extends JavaPlugin {
    private MongoPunishmentRepository punishmentRepository;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            punishmentRepository = MongoPunishmentRepository.fromConfig(getConfig());
        } catch (RuntimeException exception) {
            getLogger().severe("Could not connect to MongoDB: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        InfoCommand command = new InfoCommand(this, punishmentRepository);
        if (!registerCommand("info", command, command)) {
            return;
        }

        CountCommand addMuteCommand = new CountCommand(
                this,
                punishmentRepository,
                "punishmentinfo.manage",
                "Added a mute",
                punishmentRepository::addMute
        );
        if (!registerCommand("addmute", addMuteCommand, addMuteCommand)) {
            return;
        }

        CountCommand addBanCommand = new CountCommand(
                this,
                punishmentRepository,
                "punishmentinfo.manage",
                "Added a ban",
                punishmentRepository::addBan
        );
        if (!registerCommand("addban", addBanCommand, addBanCommand)) {
            return;
        }

        CountCommand resetCommand = new CountCommand(
                this,
                punishmentRepository,
                "punishmentinfo.manage",
                "Reset punishments",
                punishmentRepository::resetPunishments
        );
        if (!registerCommand("resetpunishments", resetCommand, resetCommand)) {
            return;
        }

        if (!registerCommand(
                "punishstats",
                new PunishStatsCommand(this, punishmentRepository, "punishmentinfo.stats"),
                null
        )) {
            return;
        }
    }

    @Override
    public void onDisable() {
        if (punishmentRepository != null) {
            punishmentRepository.close();
        }
    }

    private boolean registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command /" + name + " is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }

        command.setExecutor(executor);
        if (tabCompleter != null) {
            command.setTabCompleter(tabCompleter);
        }
        return true;
    }
}
