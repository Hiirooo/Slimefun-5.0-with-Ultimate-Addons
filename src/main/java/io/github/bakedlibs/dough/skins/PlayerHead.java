package io.github.bakedlibs.dough.skins;

import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Skull;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import io.github.bakedlibs.dough.versions.UnknownServerVersionException;

/**
 * Patched version of Dough's PlayerHead for modern Paper versions.
 *
 * <p>It avoids fragile NMS reflection for skull updates and uses the stable
 * Bukkit/Paper API ({@link Skull#setOwnerProfile(PlayerProfile)}).</p>
 */
public final class PlayerHead {

    private PlayerHead() {}

    /**
     * This Method will simply return the Head of the specified Player
     *
     * @param player
     *            The Owner of your Head
     *
     * @return A new Head Item for the specified Player
     */
    public static @Nonnull ItemStack getItemStack(@Nonnull OfflinePlayer player) {
        Validate.notNull(player, "The player can not be null!");

        return getItemStack(meta -> meta.setOwningPlayer(player));
    }

    /**
     * This Method will simply return the Head of the specified Player
     *
     * @param skin
     *            The skin of the head you want.
     *
     * @return A new Head Item for the specified Player
     */
    public static @Nonnull ItemStack getItemStack(@Nonnull PlayerSkin skin) {
        Validate.notNull(skin, "The skin can not be null!");

        return getItemStack(meta -> {
            try {
                skin.getProfile().apply(meta);
            } catch (NoSuchFieldException | IllegalAccessException | UnknownServerVersionException e) {
                e.printStackTrace();
            }
        });
    }

    private static @Nonnull ItemStack getItemStack(@Nonnull Consumer<SkullMeta> consumer) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        consumer.accept(meta);
        item.setItemMeta(meta);
        return item;
    }

    @ParametersAreNonnullByDefault
    public static void setSkin(Block block, PlayerSkin skin, boolean sendBlockUpdate) {
        Material material = block.getType();

        if (material != Material.PLAYER_HEAD && material != Material.PLAYER_WALL_HEAD) {
            throw new IllegalArgumentException("Cannot update a head texture. Expected a Player Head, received: " + material);
        }

        BlockState state = block.getState();
        if (!(state instanceof Skull skull)) {
            throw new IllegalArgumentException("Cannot update a head texture. Expected Skull block state, got: " + state.getClass().getName());
        }

        CustomGameProfile profile = skin.getProfile();
        PlayerProfile bukkitProfile = org.bukkit.Bukkit.createPlayerProfile(profile.getId(), "CS-CoreLib");
        PlayerTextures textures = bukkitProfile.getTextures();
        textures.setSkin(profile.getSkinUrl());
        bukkitProfile.setTextures(textures);

        skull.setOwnerProfile(bukkitProfile);

        if (sendBlockUpdate) {
            skull.update(true, false);
        }
    }

}
