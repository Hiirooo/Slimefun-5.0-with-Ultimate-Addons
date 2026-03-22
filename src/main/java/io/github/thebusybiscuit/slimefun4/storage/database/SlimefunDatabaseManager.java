package io.github.thebusybiscuit.slimefun4.storage.database;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlimefunDatabaseManager {

    private static final String PROFILE_CONFIG = "profile-storage.yml";
    private static final String BLOCK_CONFIG = "block-storage.yml";

    private final JavaPlugin plugin;
    private YamlConfiguration profileConfig;
    private YamlConfiguration blockConfig;

    public SlimefunDatabaseManager(@Nonnull JavaPlugin plugin) {
        this.plugin = plugin;
        this.profileConfig = loadOrCreate(PROFILE_CONFIG);
        this.blockConfig = loadOrCreate(BLOCK_CONFIG);
    }

    public @Nonnull StorageType getProfileStorageType() {
        return StorageType.fromString(profileConfig.getString("storageType"), StorageType.LEGACY);
    }

    public @Nonnull StorageType getBlockStorageType() {
        return StorageType.fromString(blockConfig.getString("storageType"), StorageType.LEGACY);
    }

    public @Nonnull String getProfileSqlitePath() {
        return profileConfig.getString("sqlite.file", "data-storage/Slimefun/profile-data.db");
    }

    public @Nonnull String getBlockSqlitePath() {
        return blockConfig.getString("sqlite.file", "data-storage/Slimefun/block-data.db");
    }

    public @Nonnull String getProfilePostgresUrl() {
        String host = profileConfig.getString("postgresql.host", "localhost");
        int port = profileConfig.getInt("postgresql.port", 5432);
        String database = profileConfig.getString("postgresql.database", "postgres");
        String sslMode = profileConfig.getString("postgresql.sslMode", "require");
        return "jdbc:postgresql://" + host + ':' + port + '/' + database + "?sslmode=" + sslMode;
    }

    public @Nonnull String getProfilePostgresUser() {
        return profileConfig.getString("postgresql.user", "postgres");
    }

    public @Nonnull String getProfilePostgresPassword() {
        return profileConfig.getString("postgresql.password", "");
    }

    public @Nonnull String getBlockPostgresUrl() {
        String host = blockConfig.getString("postgresql.host", "localhost");
        int port = blockConfig.getInt("postgresql.port", 5432);
        String database = blockConfig.getString("postgresql.database", "postgres");
        String sslMode = blockConfig.getString("postgresql.sslMode", "require");
        return "jdbc:postgresql://" + host + ':' + port + '/' + database + "?sslmode=" + sslMode;
    }

    public @Nonnull String getBlockPostgresUser() {
        return blockConfig.getString("postgresql.user", "postgres");
    }

    public @Nonnull String getBlockPostgresPassword() {
        return blockConfig.getString("postgresql.password", "");
    }

    public void reload() {
        this.profileConfig = loadOrCreate(PROFILE_CONFIG);
        this.blockConfig = loadOrCreate(BLOCK_CONFIG);
    }

    public void validateAndLog() {
        StorageType profile = getProfileStorageType();
        StorageType block = getBlockStorageType();

        if (profile == StorageType.MYSQL) {
            plugin.getLogger().log(Level.WARNING, "Profile storage type {0} is configured but not implemented yet, falling back to LEGACY", profile);
        }

        if (block == StorageType.MYSQL) {
            plugin.getLogger().log(Level.WARNING, "Block storage type {0} is configured but not implemented yet, falling back to LEGACY", block);
        }
    }

    private @Nonnull YamlConfiguration loadOrCreate(@Nonnull String resourceName) {
        File file = new File(plugin.getDataFolder(), resourceName);

        if (!file.exists()) {
            plugin.saveResource(resourceName, false);
        }

        YamlConfiguration cfg = new YamlConfiguration();
        try {
            cfg.load(file);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load " + resourceName + ", attempting defaults", e);

            try {
                cfg.save(file);
            } catch (IOException ignored) {
                // ignored
            }
        }

        return cfg;
    }
}
