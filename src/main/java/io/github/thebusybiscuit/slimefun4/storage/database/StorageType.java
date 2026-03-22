package io.github.thebusybiscuit.slimefun4.storage.database;

public enum StorageType {
    LEGACY,
    SQLITE,
    MYSQL,
    POSTGRESQL;

    public static StorageType fromString(String value, StorageType fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return StorageType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
