package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.Storage;
import io.github.thebusybiscuit.slimefun4.storage.backend.legacy.LegacyStorage;
import io.github.thebusybiscuit.slimefun4.storage.backend.postgresql.PostgreSqlStorage;
import io.github.thebusybiscuit.slimefun4.storage.backend.sqlite.SqliteStorage;
import io.github.thebusybiscuit.slimefun4.storage.database.StorageType;
import java.io.File;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

class MigrateCommand extends SubCommand {

    private static volatile boolean migrating = false;

    MigrateCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "migrate", true);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.migrate.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!sender.hasPermission("slimefun.command.migrate")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        Storage storage = Slimefun.getPlayerStorage();
        StorageType configuredType = Slimefun.getDatabaseManager().getProfileStorageType();
        boolean runtimeSqlStorage = storage instanceof SqliteStorage || storage instanceof PostgreSqlStorage;

        if (!runtimeSqlStorage) {
            if (configuredType == StorageType.SQLITE || configuredType == StorageType.POSTGRESQL) {
                Slimefun.getLocalization().sendMessage(sender, "commands.migrate.restart-required", true);
            } else {
                Slimefun.getLocalization().sendMessage(sender, "commands.migrate.not-sqlite", true);
            }

            return;
        }

        if (args.length <= 1 || !"confirm".equalsIgnoreCase(args[1])) {
            Slimefun.getLocalization().sendMessage(sender, "commands.migrate.confirm", true);
            return;
        }

        if (migrating) {
            Slimefun.getLocalization().sendMessage(sender, "commands.migrate.in-progress", true);
            return;
        }

        migrating = true;
        Slimefun.getLocalization().sendMessage(sender, "commands.migrate.started", true);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                File playersFolder = new File("data-storage/Slimefun/Players");
                File[] files = playersFolder.listFiles((dir, name) -> name.endsWith(".yml"));

                if (files == null || files.length == 0) {
                    sendSync(sender, "commands.migrate.already-migrated");
                    return;
                }

                LegacyStorage legacyStorage = new LegacyStorage();
                int migratedCount = 0;
                int failedCount = 0;

                for (File file : files) {
                    String fileName = file.getName();
                    String uuidString = fileName.substring(0, fileName.length() - 4);

                    try {
                        var uuid = java.util.UUID.fromString(uuidString);
                        var data = legacyStorage.loadPlayerData(uuid);
                        storage.savePlayerData(uuid, data);
                        migratedCount++;
                    } catch (Exception e) {
                        failedCount++;
                        plugin.getLogger().log(Level.WARNING, "Failed to migrate player profile from file: " + fileName, e);
                    }
                }

                int totalCount = files.length;
                int finalMigrated = migratedCount;
                int finalFailed = failedCount;

                sendSync(sender, "commands.migrate.success", msg -> msg.replace("%migrated%", String.valueOf(finalMigrated))
                        .replace("%total%", String.valueOf(totalCount))
                        .replace("%failed%", String.valueOf(finalFailed)));
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Unexpected error while running /sf migrate", e);
                sendSync(sender, "commands.migrate.failed");
            } finally {
                migrating = false;
            }
        });
    }

    private void sendSync(@Nonnull CommandSender sender, @Nonnull String key) {
        Slimefun.runSync(() -> Slimefun.getLocalization().sendMessage(sender, key, true));
    }

    private void sendSync(@Nonnull CommandSender sender, @Nonnull String key, @Nonnull java.util.function.UnaryOperator<String> transformer) {
        Slimefun.runSync(() -> Slimefun.getLocalization().sendMessage(sender, key, true, transformer));
    }
}
