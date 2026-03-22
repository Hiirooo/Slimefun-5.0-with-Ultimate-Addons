package io.github.thebusybiscuit.slimefun4.storage.backend.sqlite;

import io.github.thebusybiscuit.slimefun4.api.gps.Waypoint;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerBackpack;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.Storage;
import io.github.thebusybiscuit.slimefun4.storage.backend.legacy.LegacyStorage;
import io.github.thebusybiscuit.slimefun4.storage.data.PlayerData;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

public final class SqliteStorage implements Storage {

    private static final String CREATE_TABLE_SQL =
            "CREATE TABLE IF NOT EXISTS sf_player_data ("
                    + "uuid TEXT PRIMARY KEY NOT NULL,"
                    + "player_data TEXT NOT NULL,"
                    + "waypoints_data TEXT NOT NULL,"
                    + "updated_at INTEGER NOT NULL"
                    + ");";

    private static final String SELECT_SQL =
            "SELECT player_data, waypoints_data FROM sf_player_data WHERE uuid = ?;";

    private static final String UPSERT_SQL =
            "INSERT INTO sf_player_data (uuid, player_data, waypoints_data, updated_at) VALUES (?, ?, ?, ?) "
                    + "ON CONFLICT(uuid) DO UPDATE SET "
                    + "player_data = excluded.player_data, "
                    + "waypoints_data = excluded.waypoints_data, "
                    + "updated_at = excluded.updated_at;";

    private final String jdbcUrl;
    private final LegacyStorage legacyStorage = new LegacyStorage();

    public SqliteStorage(@Nonnull String databasePath) {
        File file = new File(databasePath);
        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create directory for sqlite storage: " + parent.getAbsolutePath());
        }

        this.jdbcUrl = "jdbc:sqlite:" + file.getAbsolutePath();
        init();
    }

    private void init() {
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(CREATE_TABLE_SQL)) {
            stmt.execute();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize sqlite storage", e);
        }
    }

    @Override
    public @Nonnull PlayerData loadPlayerData(@Nonnull UUID uuid) {
        long start = System.nanoTime();

        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(SELECT_SQL)) {
            stmt.setString(1, uuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return migrateLegacyData(uuid, start);
                }

                YamlConfiguration playerDataYaml = readYaml(rs.getString("player_data"));
                YamlConfiguration waypointsYaml = readYaml(rs.getString("waypoints_data"));
                return parseData(uuid, playerDataYaml, waypointsYaml, start);
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load player data from sqlite for " + uuid, e);
            return emptyPlayerData(start);
        }
    }

    @Override
    public void savePlayerData(@Nonnull UUID uuid, @Nonnull PlayerData data) {
        long start = System.nanoTime();

        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(UPSERT_SQL)) {
            YamlConfiguration playerDataYaml = toPlayerYaml(data);
            YamlConfiguration waypointsYaml = toWaypointsYaml(data);

            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerDataYaml.saveToString());
            stmt.setString(3, waypointsYaml.saveToString());
            stmt.setLong(4, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to save player data to sqlite for " + uuid, e);
        }

        long end = System.nanoTime();
        Slimefun.getAnalyticsService().recordPlayerProfileDataTime("sqlite", false, end - start);
    }

    private @Nonnull PlayerData parseData(
            @Nonnull UUID uuid,
            @Nonnull YamlConfiguration playerDataYaml,
            @Nonnull YamlConfiguration waypointsYaml,
            long start) {
        Set<Research> researches = new HashSet<>();
        for (Research research : Slimefun.getRegistry().getResearches()) {
            if (playerDataYaml.contains("researches." + research.getID())) {
                researches.add(research);
            }
        }

        HashMap<Integer, PlayerBackpack> backpacks = new HashMap<>();
        if (playerDataYaml.isConfigurationSection("backpacks")) {
            for (String key : playerDataYaml.getConfigurationSection("backpacks").getKeys(false)) {
                try {
                    int id = Integer.parseInt(key);
                    int size = playerDataYaml.getInt("backpacks." + key + ".size");

                    HashMap<Integer, ItemStack> items = new HashMap<>();
                    for (int i = 0; i < size; i++) {
                        items.put(i, playerDataYaml.getItemStack("backpacks." + key + ".contents." + i));
                    }

                    PlayerBackpack backpack = PlayerBackpack.load(uuid, id, size, items);
                    backpacks.put(id, backpack);
                } catch (Exception x) {
                    Slimefun.logger().log(
                            Level.WARNING,
                            x,
                            () -> "Could not load Backpack \"" + key + "\" for Player \"" + uuid + '\"');
                }
            }
        }

        Set<Waypoint> waypoints = new HashSet<>();
        for (String key : waypointsYaml.getKeys(false)) {
            try {
                String worldName = waypointsYaml.getString(key + ".world");

                if (worldName != null && Bukkit.getWorld(worldName) != null) {
                    String waypointName = waypointsYaml.getString(key + ".name");
                    Location location = waypointsYaml.getLocation(key);
                    if (location != null) {
                        waypoints.add(new Waypoint(uuid, key, location, waypointName));
                    }
                }
            } catch (Exception x) {
                Slimefun.logger().log(
                        Level.WARNING,
                        x,
                        () -> "Could not load Waypoint \"" + key + "\" for Player \"" + uuid + '\"');
            }
        }

        long end = System.nanoTime();
        Slimefun.getAnalyticsService().recordPlayerProfileDataTime("sqlite", true, end - start);
        return new PlayerData(researches, backpacks, waypoints);
    }

    private @Nonnull YamlConfiguration toPlayerYaml(@Nonnull PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Research research : data.getResearches()) {
            yaml.set("researches." + research.getID(), true);
        }

        for (PlayerBackpack backpack : data.getBackpacks().values()) {
            yaml.set("backpacks." + backpack.getId() + ".size", backpack.getSize());
            for (int i = 0; i < backpack.getSize(); i++) {
                ItemStack item = backpack.getInventory().getItem(i);
                if (item != null) {
                    yaml.set("backpacks." + backpack.getId() + ".contents." + i, item);
                }
            }
        }

        return yaml;
    }

    private @Nonnull YamlConfiguration toWaypointsYaml(@Nonnull PlayerData data) {
        YamlConfiguration yaml = new YamlConfiguration();

        for (Waypoint waypoint : data.getWaypoints()) {
            yaml.set(waypoint.getId(), waypoint.getLocation());
            yaml.set(waypoint.getId() + ".name", waypoint.getName());
        }

        return yaml;
    }

    private @Nonnull YamlConfiguration readYaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        if (text != null && !text.isBlank()) {
            yaml.loadFromString(text);
        }

        return yaml;
    }

    private @Nonnull PlayerData emptyPlayerData(long start) {
        long end = System.nanoTime();
        Slimefun.getAnalyticsService().recordPlayerProfileDataTime("sqlite", true, end - start);
        return new PlayerData(new HashSet<>(), new HashMap<>(), new HashSet<>());
    }

    private @Nonnull PlayerData migrateLegacyData(@Nonnull UUID uuid, long start) {
        try {
            PlayerData legacyData = legacyStorage.loadPlayerData(uuid);
            savePlayerData(uuid, legacyData);
            Slimefun.logger().log(Level.INFO, "Migrated player profile data from legacy files to sqlite for {0}", uuid);
            return legacyData;
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "Failed legacy->sqlite profile migration for " + uuid, e);
            return emptyPlayerData(start);
        }
    }

    private Connection openConnection() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }

        return DriverManager.getConnection(jdbcUrl);
    }
}
