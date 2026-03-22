package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;

import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;

/**
 * The {@link EnergyRegulator} is a special type of {@link SlimefunItem} which serves as the heart of every
 * {@link EnergyNet}.
 * 
 * @author TheBusyBiscuit
 * 
 * @see EnergyNet
 * @see EnergyNetComponent
 *
 */
public class EnergyRegulator extends SlimefunItem implements HologramOwner {

    private static final int PAGE_SIZE = 45;

    @ParametersAreNonnullByDefault
    public EnergyRegulator(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(onBreak());
        addItemHandler(onUse());
    }

    @Nonnull
    private BlockUseHandler onUse() {
        return new BlockUseHandler() {

            @Override
            public void onRightClick(PlayerRightClickEvent e) {
                Block block = e.getClickedBlock().orElse(null);

                if (block == null) {
                    return;
                }

                e.cancel();

                Location regulator = block.getLocation();
                EnergyNet network = EnergyNet.getNetworkFromLocationOrCreate(regulator);
                openOverviewMenu(e, network, regulator);
            }
        };
    }

    private void openOverviewMenu(@Nonnull PlayerRightClickEvent event, @Nonnull EnergyNet network, @Nonnull Location regulator) {
        ChestMenu menu = new ChestMenu("Energy Regulator");
        menu.setEmptySlotsClickable(false);

        for (int i = 0; i < 27; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        List<GroupedNode> generators = groupByName(network.getGenerators().keySet());
        List<GroupedNode> capacitors = groupByName(network.getCapacitors().keySet());
        List<GroupedNode> consumers = groupByName(network.getConsumers().keySet());

        menu.addItem(4, getItem().clone(), ChestMenuUtils.getEmptyClickHandler());

        menu.addItem(11, CustomItemStack.create(Material.LIME_STAINED_GLASS, "&aGenerators",
                "&7Nodes: &f" + network.getGenerators().size(),
                "&7Machine Types: &f" + generators.size(),
                "",
                "&eClick to open"), (p, slot, item, action) -> {
                    openDetailsMenu(event, regulator, "Generators", generators, 1);
                    return false;
                });

        menu.addItem(13, CustomItemStack.create(Material.LIGHT_BLUE_STAINED_GLASS, "&bCapacitors",
                "&7Nodes: &f" + network.getCapacitors().size(),
                "&7Machine Types: &f" + capacitors.size(),
                "",
                "&eClick to open"), (p, slot, item, action) -> {
                    openDetailsMenu(event, regulator, "Capacitors", capacitors, 1);
                    return false;
                });

        menu.addItem(15, CustomItemStack.create(Material.YELLOW_STAINED_GLASS, "&eConsumers",
                "&7Nodes: &f" + network.getConsumers().size(),
                "&7Machine Types: &f" + consumers.size(),
                "",
                "&eClick to open"), (p, slot, item, action) -> {
                    openDetailsMenu(event, regulator, "Consumers", consumers, 1);
                    return false;
                });

        menu.open(event.getPlayer());
    }

    private void openDetailsMenu(@Nonnull PlayerRightClickEvent event, @Nonnull Location regulator, @Nonnull String title, @Nonnull List<GroupedNode> grouped, int page) {
        ChestMenu menu = new ChestMenu("Energy " + title + " - P" + page);
        menu.setEmptySlotsClickable(false);

        for (int i = 0; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        int index = PAGE_SIZE * (page - 1);
        int rendered = Math.min(PAGE_SIZE, grouped.size() - index);

        for (int i = 0; i < rendered; i++) {
            GroupedNode node = grouped.get(index + i);
            menu.addItem(i, node.toItemStack(), ChestMenuUtils.getEmptyClickHandler());
        }

        int pages = Math.max(1, (int) Math.ceil(grouped.size() / (double) PAGE_SIZE));

        menu.addItem(49, ChestMenuUtils.getBackButton(event.getPlayer(), "&7Back"), (p, slot, item, action) -> {
            openOverviewMenu(event, EnergyNet.getNetworkFromLocationOrCreate(regulator), regulator);
            return false;
        });

        if (page > 1) {
            menu.addItem(45, ChestMenuUtils.getPreviousButton(event.getPlayer(), page, pages), (p, slot, item, action) -> {
                openDetailsMenu(event, regulator, title, grouped, page - 1);
                return false;
            });
        }

        if (page < pages) {
            menu.addItem(53, ChestMenuUtils.getNextButton(event.getPlayer(), page, pages), (p, slot, item, action) -> {
                openDetailsMenu(event, regulator, title, grouped, page + 1);
                return false;
            });
        }

        menu.open(event.getPlayer());
    }

    @Nonnull
    private List<GroupedNode> groupByName(@Nonnull Set<Location> locations) {
        Map<String, GroupedNode> grouped = new LinkedHashMap<>();

        for (Location location : locations) {
            SlimefunItem item = BlockStorage.check(location);
            String name = item != null ? item.getItemName() : "Unknown Machine";
            ItemStack icon = item != null ? item.getItem() : new ItemStack(Material.BARRIER);

            GroupedNode node = grouped.computeIfAbsent(name, n -> new GroupedNode(n, icon));
            String locText = formatLocation(location);
            Integer durability = readDurability(location);
            node.addNode(locText, durability);
        }

        List<GroupedNode> nodes = new ArrayList<>(grouped.values());
        nodes.sort(Comparator.comparing(n -> n.name));
        return nodes;
    }

    @Nonnull
    private String formatLocation(@Nonnull Location location) {
        return location.getWorld().getName() + " @ " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private Integer readDurability(@Nonnull Location location) {
        String durability = BlockStorage.getLocationInfo(location, "dynatech:durability");
        if (durability == null) {
            durability = BlockStorage.getLocationInfo(location, "durability");
        }

        if (durability == null) {
            return null;
        }

        try {
            return Integer.parseInt(durability);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class NodeEntry {
        private final String coordinate;
        private final Integer durability;

        private NodeEntry(@Nonnull String coordinate, Integer durability) {
            this.coordinate = coordinate;
            this.durability = durability;
        }
    }

    private static final class GroupedNode {
        private final String name;
        private final ItemStack icon;
        private final List<NodeEntry> entries = new ArrayList<>();

        private GroupedNode(@Nonnull String name, @Nonnull ItemStack icon) {
            this.name = name;
            this.icon = icon.clone();
            this.icon.setAmount(1);
        }

        private void addNode(@Nonnull String coordinate, Integer durability) {
            entries.add(new NodeEntry(coordinate, durability));
        }

        @Nonnull
        private ItemStack toItemStack() {
            List<String> lore = new ArrayList<>();
            lore.add("&7Total: &f" + entries.size());

            int withDurability = 0;
            int durabilitySum = 0;
            int minDurability = Integer.MAX_VALUE;
            int maxDurability = Integer.MIN_VALUE;
            for (NodeEntry entry : entries) {
                if (entry.durability != null) {
                    withDurability++;
                    durabilitySum += entry.durability;
                    minDurability = Math.min(minDurability, entry.durability);
                    maxDurability = Math.max(maxDurability, entry.durability);
                }
            }

            if (withDurability > 0) {
                lore.add("&7Durability avg/min/max: &f" + (durabilitySum / withDurability) + "&7/&f" + minDurability + "&7/&f" + maxDurability);
            }

            lore.add("&8Coordinates:");

            int shown = Math.min(8, entries.size());
            for (int i = 0; i < shown; i++) {
                NodeEntry entry = entries.get(i);
                String suffix = entry.durability != null ? " &8| &6Durability: &f" + entry.durability : "";
                lore.add("&7- " + entry.coordinate + suffix);
            }

            if (entries.size() > shown) {
                lore.add("&8... +" + (entries.size() - shown) + " more");
            }

            return CustomItemStack.create(icon, "&f" + name + " &7x" + entries.size(), lore.toArray(new String[0]));
        }
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);
            }
        };
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                updateHologram(e.getBlock(), "&7Connecting...");
            }

        };
    }

    @Override
    public void preRegister() {
        addItemHandler(onPlace());

        addItemHandler(new BlockTicker() {

            @Override
            public boolean isSynchronized() {
                return false;
            }

            @Override
            public void tick(Block b, SlimefunItem item, Config data) {
                EnergyRegulator.this.tick(b);
            }
        });
    }

    private void tick(@Nonnull Block b) {
        EnergyNet network = EnergyNet.getNetworkFromLocationOrCreate(b.getLocation());
        network.tick(b);
    }

}
