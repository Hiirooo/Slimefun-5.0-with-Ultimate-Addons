package me.mrCookieSlime.Slimefun.api.inventory;

import java.io.File;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.storage.backend.sqlite.SqliteDataStore;
import org.bukkit.configuration.file.YamlConfiguration;

// This class will be deprecated, relocated and rewritten in a future version.
public class UniversalBlockMenu extends DirtyChestMenu {

    public UniversalBlockMenu(BlockMenuPreset preset) {
        super(preset);

        preset.clone(this);

        save();
    }

    public UniversalBlockMenu(BlockMenuPreset preset, Config cfg) {
        super(preset);

        for (int i = 0; i < 54; i++) {
            if (cfg.contains(String.valueOf(i))) {
                addItem(i, cfg.getItem(String.valueOf(i)));
            }
        }

        preset.clone(this);

        if (preset.getSize() > -1 && !preset.getPresetSlots().contains(preset.getSize() - 1) && cfg.contains(String.valueOf(preset.getSize() - 1))) {
            addItem(preset.getSize() - 1, cfg.getItem(String.valueOf(preset.getSize() - 1)));
        }

        this.getContents();
    }

    public UniversalBlockMenu(BlockMenuPreset preset, YamlConfiguration cfg) {
        super(preset);

        for (int i = 0; i < 54; i++) {
            if (cfg.contains(String.valueOf(i))) {
                addItem(i, cfg.getItemStack(String.valueOf(i)));
            }
        }

        preset.clone(this);

        if (preset.getSize() > -1 && !preset.getPresetSlots().contains(preset.getSize() - 1) && cfg.contains(String.valueOf(preset.getSize() - 1))) {
            addItem(preset.getSize() - 1, cfg.getItemStack(String.valueOf(preset.getSize() - 1)));
        }

        this.getContents();
    }

    public void save() {
        if (!isDirty()) {
            return;
        }

        // To force CS-CoreLib to build the Inventory
        this.getContents();

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("preset", preset.getID());

        for (int slot : preset.getInventorySlots()) {
            cfg.set(String.valueOf(slot), getItemInSlot(slot));
        }

        if (SqliteDataStore.isEnabled()) {
            SqliteDataStore.saveUniversalInventory(preset.getID(), cfg.saveToString());
        } else {
            File file = new File("data-storage/Slimefun/universal-inventories/" + preset.getID() + ".sfi");
            Config legacyCfg = new Config(file);
            legacyCfg.setValue("preset", preset.getID());

            for (int slot : preset.getInventorySlots()) {
                legacyCfg.setValue(String.valueOf(slot), getItemInSlot(slot));
            }

            legacyCfg.save();
        }

        changes = 0;
    }

    public String toStorageYaml() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("preset", preset.getID());

        for (int slot : preset.getInventorySlots()) {
            cfg.set(String.valueOf(slot), getItemInSlot(slot));
        }

        return cfg.saveToString();
    }

}
