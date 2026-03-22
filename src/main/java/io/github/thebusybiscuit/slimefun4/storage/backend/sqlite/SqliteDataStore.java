package io.github.thebusybiscuit.slimefun4.storage.backend.sqlite;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.storage.database.StorageType;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockInfoConfig;
import org.bukkit.Location;
import org.bukkit.World;

public final class SqliteDataStore {

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);
    private static volatile StorageType initializedForType = null;

    private SqliteDataStore() {}

    private static @Nonnull StorageType getActiveStorageType() {
        return Slimefun.getDatabaseManager().getBlockStorageType();
    }

    public static boolean isEnabled() {
        StorageType type = getActiveStorageType();
        return type == StorageType.SQLITE || type == StorageType.POSTGRESQL;
    }

    public static void initIfEnabled() {
        StorageType activeType = getActiveStorageType();
        if (!isEnabled()) {
            return;
        }

        if (INITIALIZED.get() && initializedForType == activeType) {
            return;
        }

        synchronized (INITIALIZED) {
            if (INITIALIZED.get() && initializedForType == activeType) {
                return;
            }

            try (Connection conn = openConnection()) {
                execute(conn, "CREATE TABLE IF NOT EXISTS sf_block_data (world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, id TEXT NOT NULL, data_json TEXT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(world, x, y, z));");
                execute(conn, "CREATE TABLE IF NOT EXISTS sf_chunk_data (world TEXT NOT NULL, chunk_x INTEGER NOT NULL, chunk_z INTEGER NOT NULL, data_json TEXT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(world, chunk_x, chunk_z));");
                execute(conn, "CREATE TABLE IF NOT EXISTS sf_block_inventory (world TEXT NOT NULL, x INTEGER NOT NULL, y INTEGER NOT NULL, z INTEGER NOT NULL, preset TEXT NOT NULL, data_yaml TEXT NOT NULL, updated_at BIGINT NOT NULL, PRIMARY KEY(world, x, y, z));");
                execute(conn, "CREATE TABLE IF NOT EXISTS sf_universal_inventory (preset TEXT PRIMARY KEY NOT NULL, data_yaml TEXT NOT NULL, updated_at BIGINT NOT NULL);");

                if (activeType == StorageType.POSTGRESQL) {
                    applyPostgreSqlPatches(conn);
                }

                INITIALIZED.set(true);
                initializedForType = activeType;
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize SQL data store for " + activeType, e);
            }
        }
    }

    public static @Nonnull Map<Location, BlockInfoConfig> loadBlockData(@Nonnull World world) {
        initIfEnabled();
        Map<Location, BlockInfoConfig> result = new HashMap<>();

        String sql = "SELECT x, y, z, data_json FROM sf_block_data WHERE world = ?;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    result.put(location, new BlockInfoConfig(parseJsonMap(rs.getString("data_json"))));
                }
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load block data from SQL storage for world " + world.getName(), e);
        }

        return result;
    }

    public static @Nonnull Map<String, BlockInfoConfig> loadChunkData(@Nonnull World world) {
        initIfEnabled();
        Map<String, BlockInfoConfig> result = new HashMap<>();

        String sql = "SELECT chunk_x, chunk_z, data_json FROM sf_chunk_data WHERE world = ?;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    int x = rs.getInt("chunk_x");
                    int z = rs.getInt("chunk_z");
                    String key = world.getName() + ";Chunk;" + x + ';' + z;
                    result.put(key, new BlockInfoConfig(parseJsonMap(rs.getString("data_json"))));
                }
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load chunk data from SQL storage for world " + world.getName(), e);
        }

        return result;
    }

    public static @Nonnull Map<Location, String> loadBlockInventories(@Nonnull World world) {
        initIfEnabled();
        Map<Location, String> result = new HashMap<>();

        String sql = "SELECT x, y, z, data_yaml FROM sf_block_inventory WHERE world = ?;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world.getName());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Location location = new Location(world, rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
                    result.put(location, rs.getString("data_yaml"));
                }
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load block inventories from SQL storage for world " + world.getName(), e);
        }

        return result;
    }

    public static @Nonnull Map<String, String> loadUniversalInventories() {
        initIfEnabled();
        Map<String, String> result = new HashMap<>();

        String sql = "SELECT preset, data_yaml FROM sf_universal_inventory;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql); ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                result.put(rs.getString("preset"), rs.getString("data_yaml"));
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load universal inventories from SQL storage", e);
        }

        return result;
    }

    public static void applyBlockDataChanges(@Nonnull Map<String, Config> changes) {
        if (changes.isEmpty()) {
            return;
        }

        initIfEnabled();

        String upsertSql = "INSERT INTO sf_block_data (world, x, y, z, id, data_json, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(world, x, y, z) DO UPDATE SET id = excluded.id, data_json = excluded.data_json, updated_at = excluded.updated_at;";
        String deleteSql = "DELETE FROM sf_block_data WHERE world = ? AND x = ? AND y = ? AND z = ?;";

        try (Connection conn = openConnection();
                PreparedStatement upsert = conn.prepareStatement(upsertSql);
                PreparedStatement delete = conn.prepareStatement(deleteSql)) {
            conn.setAutoCommit(false);

            for (Map.Entry<String, Config> grouped : changes.entrySet()) {
                String id = grouped.getKey();
                Config config = grouped.getValue();

                for (String serializedLocation : config.getKeys()) {
                    String[] split = serializedLocation.split(";");
                    if (split.length != 4) {
                        continue;
                    }

                    String world = split[0];
                    int x = Integer.parseInt(split[1]);
                    int y = Integer.parseInt(split[2]);
                    int z = Integer.parseInt(split[3]);
                    String value = config.getString(serializedLocation);

                    if (value == null) {
                        bindLocation(delete, world, x, y, z);
                        delete.addBatch();
                    } else {
                        upsert.setString(1, world);
                        upsert.setInt(2, x);
                        upsert.setInt(3, y);
                        upsert.setInt(4, z);
                        upsert.setString(5, id);
                        upsert.setString(6, value);
                        upsert.setLong(7, System.currentTimeMillis());
                        upsert.addBatch();
                    }
                }
            }

            upsert.executeBatch();
            delete.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to persist block data changes to SQL storage", e);
        }
    }

    public static void saveAllChunkData(@Nonnull Map<String, BlockInfoConfig> chunks) {
        initIfEnabled();

        String upsertSql = "INSERT INTO sf_chunk_data (world, chunk_x, chunk_z, data_json, updated_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(world, chunk_x, chunk_z) DO UPDATE SET data_json = excluded.data_json, updated_at = excluded.updated_at;";
        String deleteSql = "DELETE FROM sf_chunk_data WHERE world = ? AND chunk_x = ? AND chunk_z = ?;";

        try (Connection conn = openConnection();
                PreparedStatement upsert = conn.prepareStatement(upsertSql);
                PreparedStatement delete = conn.prepareStatement(deleteSql)) {
            conn.setAutoCommit(false);

            for (Map.Entry<String, BlockInfoConfig> entry : chunks.entrySet()) {
                String[] split = entry.getKey().split(";");
                if (split.length != 4 || !"Chunk".equals(split[1])) {
                    continue;
                }

                String world = split[0];
                int x = Integer.parseInt(split[2]);
                int z = Integer.parseInt(split[3]);
                BlockInfoConfig cfg = entry.getValue();

                if (cfg.getKeys().isEmpty()) {
                    delete.setString(1, world);
                    delete.setInt(2, x);
                    delete.setInt(3, z);
                    delete.addBatch();
                } else {
                    upsert.setString(1, world);
                    upsert.setInt(2, x);
                    upsert.setInt(3, z);
                    upsert.setString(4, cfg.toJSON());
                    upsert.setLong(5, System.currentTimeMillis());
                    upsert.addBatch();
                }
            }

            upsert.executeBatch();
            delete.executeBatch();
            conn.commit();
            conn.setAutoCommit(true);
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to persist chunk data to SQL storage", e);
        }
    }

    public static void saveBlockInventory(@Nonnull Location location, @Nonnull String preset, @Nonnull String dataYaml) {
        initIfEnabled();

        String sql = "INSERT INTO sf_block_inventory (world, x, y, z, preset, data_yaml, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT(world, x, y, z) DO UPDATE SET preset = excluded.preset, data_yaml = excluded.data_yaml, updated_at = excluded.updated_at;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, location.getWorld().getName());
            stmt.setInt(2, location.getBlockX());
            stmt.setInt(3, location.getBlockY());
            stmt.setInt(4, location.getBlockZ());
            stmt.setString(5, preset);
            stmt.setString(6, dataYaml);
            stmt.setLong(7, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to save block inventory to SQL storage", e);
        }
    }

    public static @Nonnull String loadBlockInventoryYaml(@Nonnull Location location) {
        initIfEnabled();

        String sql = "SELECT data_yaml FROM sf_block_inventory WHERE world = ? AND x = ? AND y = ? AND z = ?;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindLocation(stmt, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("data_yaml");
                }
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to load block inventory from SQL storage", e);
        }

        return "";
    }

    public static void deleteBlockInventory(@Nonnull Location location) {
        initIfEnabled();

        String sql = "DELETE FROM sf_block_inventory WHERE world = ? AND x = ? AND y = ? AND z = ?;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindLocation(stmt, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
            stmt.executeUpdate();
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to delete block inventory from SQL storage", e);
        }
    }

    public static void saveUniversalInventory(@Nonnull String preset, @Nonnull String dataYaml) {
        initIfEnabled();

        String sql = "INSERT INTO sf_universal_inventory (preset, data_yaml, updated_at) VALUES (?, ?, ?) ON CONFLICT(preset) DO UPDATE SET data_yaml = excluded.data_yaml, updated_at = excluded.updated_at;";
        try (Connection conn = openConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, preset);
            stmt.setString(2, dataYaml);
            stmt.setLong(3, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (Exception e) {
            Slimefun.logger().log(Level.SEVERE, "Failed to save universal inventory to SQL storage", e);
        }
    }

    private static void execute(@Nonnull Connection connection, @Nonnull String sql) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.execute();
        }
    }

    private static void applyPostgreSqlPatches(@Nonnull Connection connection) throws SQLException {
        execute(connection, "ALTER TABLE sf_block_data ALTER COLUMN updated_at TYPE BIGINT;");
        execute(connection, "ALTER TABLE sf_chunk_data ALTER COLUMN updated_at TYPE BIGINT;");
        execute(connection, "ALTER TABLE sf_block_inventory ALTER COLUMN updated_at TYPE BIGINT;");
        execute(connection, "ALTER TABLE sf_universal_inventory ALTER COLUMN updated_at TYPE BIGINT;");
    }

    private static void bindLocation(@Nonnull PreparedStatement stmt, @Nonnull String world, int x, int y, int z) throws SQLException {
        stmt.setString(1, world);
        stmt.setInt(2, x);
        stmt.setInt(3, y);
        stmt.setInt(4, z);
    }

    private static @Nonnull Map<String, String> parseJsonMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.length() <= 2) {
            return map;
        }

        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        for (Map.Entry<String, com.google.gson.JsonElement> entry : obj.entrySet()) {
            map.put(entry.getKey(), entry.getValue().getAsString());
        }

        return map;
    }

    private static @Nonnull Connection openConnection() throws SQLException {
        StorageType type = getActiveStorageType();
        if (type == StorageType.POSTGRESQL) {
            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("PostgreSQL JDBC driver not found", e);
            }

            return DriverManager.getConnection(
                    Slimefun.getDatabaseManager().getBlockPostgresUrl(),
                    Slimefun.getDatabaseManager().getBlockPostgresUser(),
                    Slimefun.getDatabaseManager().getBlockPostgresPassword());
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("SQLite JDBC driver not found", e);
        }

        String dbPath = Slimefun.getDatabaseManager().getBlockSqlitePath();

        File file = new File(dbPath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Cannot create sqlite directory: " + parent.getAbsolutePath());
        }

        return DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
    }
}
