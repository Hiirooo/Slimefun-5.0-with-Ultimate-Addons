package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.database.StorageType;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.scheduler.BukkitTask;

public final class AutoConfigReloadService {

    private final Map<String, Long> lastModified = new HashMap<>();
    private StorageType lastProfileType;
    private StorageType lastBlockType;
    private volatile boolean active = false;
    private BukkitTask task;

    public void start(@Nonnull Slimefun plugin, int intervalSeconds) {
        if (intervalSeconds < 5) {
            intervalSeconds = 5;
        }

        stop();
        active = true;
        rememberCurrentState(plugin);
        int taskInterval = intervalSeconds * 20;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> poll(plugin), taskInterval, taskInterval);
        plugin.getLogger().log(Level.INFO, "Auto config reload enabled (interval: {0}s)", intervalSeconds);
    }

    public void stop() {
        active = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void poll(@Nonnull Slimefun plugin) {
        if (!active || !plugin.isEnabled()) {
            return;
        }

        if (!hasConfigChanges(plugin)) {
            return;
        }

        Slimefun.runSync(() -> reloadNow(plugin));
    }

    private boolean hasConfigChanges(@Nonnull Slimefun plugin) {
        return hasChanged(plugin, "config.yml")
                || hasChanged(plugin, "profile-storage.yml")
                || hasChanged(plugin, "block-storage.yml");
    }

    private boolean hasChanged(@Nonnull Slimefun plugin, @Nonnull String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        long current = file.exists() ? file.lastModified() : -1L;
        long previous = lastModified.getOrDefault(fileName, current);
        if (current != previous) {
            lastModified.put(fileName, current);
            return true;
        }

        return false;
    }

    private void reloadNow(@Nonnull Slimefun plugin) {
        if (!active || !plugin.isEnabled()) {
            return;
        }

        try {
            Slimefun.getCfg().reload();
            Slimefun.getDatabaseManager().reload();
            Slimefun.getDatabaseManager().validateAndLog();

            StorageType profileType = Slimefun.getDatabaseManager().getProfileStorageType();
            StorageType blockType = Slimefun.getDatabaseManager().getBlockStorageType();

            if (profileType != lastProfileType || blockType != lastBlockType) {
                plugin.getLogger().warning("Storage backend config changed. Please restart the server to apply backend switches safely.");
            }

            lastProfileType = profileType;
            lastBlockType = blockType;
            plugin.getLogger().info("Detected config changes, reloaded config.yml/profile-storage.yml/block-storage.yml");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to auto-reload Slimefun config", e);
        }
    }

    private void rememberCurrentState(@Nonnull Slimefun plugin) {
        record(plugin, "config.yml");
        record(plugin, "profile-storage.yml");
        record(plugin, "block-storage.yml");
        lastProfileType = Slimefun.getDatabaseManager().getProfileStorageType();
        lastBlockType = Slimefun.getDatabaseManager().getBlockStorageType();
    }

    private void record(@Nonnull Slimefun plugin, @Nonnull String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        lastModified.put(fileName, file.exists() ? file.lastModified() : -1L);
    }
}
