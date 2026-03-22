package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.bakedlibs.dough.items.ItemUtils;

final class GrapplingHookEntity {

    private final boolean dropItem;
    private final boolean consumeOnUse;
    private final int maxDistance;
    private boolean consumed;
    private final ItemStack sourceItem;
    private final ItemStack returnItem;
    private final Arrow arrow;
    private final Entity leashTarget;

    @ParametersAreNonnullByDefault
    GrapplingHookEntity(Player p, Arrow arrow, Entity leashTarget, boolean dropItem, boolean consumeOnUse, ItemStack returnItem, int maxDistance, ItemStack sourceItem) {
        this.arrow = arrow;
        this.consumeOnUse = consumeOnUse;
        this.maxDistance = maxDistance;
        this.sourceItem = sourceItem;
        this.returnItem = returnItem.clone();
        this.returnItem.setAmount(1);
        this.leashTarget = leashTarget;
        this.dropItem = p.getGameMode() != GameMode.CREATIVE && dropItem;
    }

    @Nonnull
    public Arrow getArrow() {
        return arrow;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void consumeIfNeeded() {
        if (consumed || !consumeOnUse || sourceItem == null) {
            return;
        }

        if (sourceItem.getType() == Material.LEAD) {
            ItemUtils.consumeItem(sourceItem, false);
            consumed = true;
        }
    }

    public void drop(@Nonnull Location l) {
        // If a grappling hook was consumed, drop one grappling hook on the floor
        if (dropItem && consumed) {
            Item item = l.getWorld().dropItem(l, returnItem.clone());
            item.setPickupDelay(16);
        }
    }

    public void remove() {
        if (arrow.isValid()) {
            arrow.remove();
        }

        if (leashTarget.isValid()) {
            leashTarget.remove();
        }
    }

    @Nonnull
    public Entity getLeashTarget() {
        return leashTarget;
    }

}
