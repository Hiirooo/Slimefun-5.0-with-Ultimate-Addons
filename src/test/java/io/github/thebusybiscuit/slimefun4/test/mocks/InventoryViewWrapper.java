package io.github.thebusybiscuit.slimefun4.test.mocks;

import org.mockbukkit.mockbukkit.inventory.InventoryViewMock;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import javax.annotation.Nonnull;
/**
 * Temporary class which implements {@link #getItem(int)} and {@link #setItem(int, ItemStack)}
 * provided {@link #getInventory(int)} and {@link #convertSlot(int)} are implemented by the backing
 * {@link InventoryView}
 * <p>
 * This class should be replaced by MockBukkit when <a href="https://github.com/MockBukkit/MockBukkit/pull/1011">this pr</a>
 * is merged.
 * <br>
 * Code is taken directly from CraftBukkit <a href="https://hub.spigotmc.org/stash/projects/SPIGOT/repos/craftbukkit/browse/src/main/java/org/bukkit/craftbukkit/inventory/CraftAbstractInventoryView.java">here</a>.
 *
 * @author md5sha256
 */
public class InventoryViewWrapper extends InventoryViewMock {

    private InventoryViewWrapper(HumanEntity player,
                                 Inventory top,
                                 Inventory bottom,
                                 InventoryType type) {
        super(player, top, bottom, type);
    }

    @Nonnull
    public static InventoryViewWrapper wrap(@Nonnull InventoryView inventoryView) {
        HumanEntity player = inventoryView.getPlayer();
        Inventory top = inventoryView.getTopInventory();
        Inventory bottom = inventoryView.getBottomInventory();
        InventoryType inventoryType = inventoryView.getType();
        return new InventoryViewWrapper(player, top, bottom, inventoryType);
    }

}
