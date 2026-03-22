package io.github.bakedlibs.dough.skins;

import java.net.URL;
import java.lang.reflect.Method;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.SkullMeta;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;

import io.github.bakedlibs.dough.reflection.ReflectionUtils;
import io.github.bakedlibs.dough.versions.MinecraftVersion;
import io.github.bakedlibs.dough.versions.UnknownServerVersionException;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

/**
 * Patched version of Dough's CustomGameProfile that uses composition instead of
 * inheritance, because Mojang made {@link GameProfile} final in 1.21.x.
 *
 * <p>The original class extended GameProfile directly. This version holds a
 * GameProfile delegate internally and exposes it via {@link #getHandle()}.</p>
 */
public final class CustomGameProfile {

    /**
     * The player name for this profile.
     * "CS-CoreLib" for historical reasons and backwards compatibility.
     */
    private static final String PLAYER_NAME = "CS-CoreLib";

    /**
     * The skin's property key.
     */
    private static final String PROPERTY_KEY = "textures";

    private final UUID profileId;
    private final GameProfile delegate;
    private final URL skinUrl;
    private final String texture;

    CustomGameProfile(@Nonnull UUID uuid, @Nullable String texture, @Nonnull URL url) {
        this.profileId = uuid;
        this.delegate = new GameProfile(uuid, PLAYER_NAME);
        this.skinUrl = url;
        this.texture = texture;

        if (texture != null) {
            putTextureProperty(delegate, texture);
        }
    }

    private static void putTextureProperty(@Nonnull GameProfile profile, @Nonnull String texture) {
        Object propertyMap = null;

        for (String accessorName : new String[] { "getProperties", "properties" }) {
            try {
                Method accessor = profile.getClass().getMethod(accessorName);
                propertyMap = accessor.invoke(profile);
                break;
            } catch (ReflectiveOperationException ignored) {
                // Try the next accessor name.
            }
        }

        if (propertyMap == null) {
            return;
        }

        Property property = new Property(PROPERTY_KEY, texture);

        for (Method method : propertyMap.getClass().getMethods()) {
            if ("put".equals(method.getName()) && method.getParameterCount() == 2) {
                try {
                    method.invoke(propertyMap, PROPERTY_KEY, property);
                    return;
                } catch (ReflectiveOperationException | IllegalArgumentException ignored) {
                    // Try the next put overload.
                }
            }
        }
    }

    /**
     * Returns the underlying {@link GameProfile} delegate.
     * Use this when you need to pass a GameProfile to NMS/reflection code.
     *
     * @return the delegate GameProfile
     */
    @Nonnull
    public GameProfile getHandle() {
        return delegate;
    }

    /**
     * Returns the UUID from the delegate profile.
     *
     * @return the profile UUID
     */
    @Nonnull
    public UUID getId() {
        return this.profileId;
    }

    void apply(@Nonnull SkullMeta meta) throws NoSuchFieldException, IllegalAccessException, UnknownServerVersionException {
        // setOwnerProfile was added in 1.18, but getOwningPlayer throws a NullPointerException since 1.20.2
        if (MinecraftVersion.get().isAtLeast(MinecraftVersion.parse("1.20"))) {
            PlayerProfile playerProfile = Bukkit.createPlayerProfile(this.getId(), PLAYER_NAME);
            PlayerTextures playerTextures = playerProfile.getTextures();
            playerTextures.setSkin(this.skinUrl);
            playerProfile.setTextures(playerTextures);
            meta.setOwnerProfile(playerProfile);
        } else {
            // Forces SkullMeta to properly deserialize and serialize the profile
            ReflectionUtils.setFieldValue(meta, "profile", this.delegate);

            meta.setOwningPlayer(meta.getOwningPlayer());

            // Now override the texture again
            ReflectionUtils.setFieldValue(meta, "profile", this.delegate);
        }
    }

    /**
     * Get the base64 encoded texture from the underline GameProfile.
     *
     * @return the base64 encoded texture.
     */
    @Nullable
    public String getBase64Texture() {
        return this.texture;
    }

    @Nonnull
    URL getSkinUrl() {
        return this.skinUrl;
    }
}
