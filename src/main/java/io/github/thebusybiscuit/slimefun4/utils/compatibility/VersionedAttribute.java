package io.github.thebusybiscuit.slimefun4.utils.compatibility;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * This class provides version-compatible access to {@link Attribute} constants.
 *
 * In Minecraft 1.21.2+, {@link Attribute} was changed from an enum to a Registry-based interface,
 * and many constants were renamed (e.g. GENERIC_MAX_HEALTH -> MAX_HEALTH).
 * This class uses Registry lookups to handle both old and new naming.
 */
public class VersionedAttribute {

    public static final Attribute MAX_HEALTH;

    static {
        // GENERIC_MAX_HEALTH is renamed to MAX_HEALTH in 1.21.2+
        MAX_HEALTH = getKey("max_health");
    }

    @Nullable
    private static Attribute getKey(@Nonnull String key) {
        return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
    }
}
