package de.larsb6.dragonegg;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DragonEgg for Paper 1.21.11.
 *
 * <p>The dragon gets extra health, every player's damage is counted and the
 * player with the most damage receives the dragon egg. Afterwards one single
 * timer runs the end lock, the glowing of the egg holder and the ENDFIGHT boss
 * bar.
 */
public final class DragonEggPlugin extends JavaPlugin {

    private final DamageTracker tracker = new DamageTracker();
    private PluginConfig config;
    private EndFightManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.config = PluginConfig.load(this, getConfig());
        this.manager = new EndFightManager(this);
        // A crash during a lock may have left the exit portal closed.
        this.manager.restoreExitPortal();

        getServer().getPluginManager().registerEvents(new DragonListener(this), this);
        getServer().getPluginManager().registerEvents(new EggListener(this), this);
        getServer().getPluginManager().registerEvents(new EndLockListener(this), this);

        register("dragonegg", new DragonEggCommand(this));
        register("endfight", new EndFightCommand(this));

        if (!this.config.isEnabled()) {
            getLogger().info("DragonEgg is disabled in config.yml (enabled: false).");
        }
    }

    private <T extends CommandExecutor & TabCompleter> void register(String name, T executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    @Override
    public void onDisable() {
        if (this.manager != null) {
            this.manager.stop();
        }
    }

    /** Re-reads the config.yml and applies the switches to the running timer. */
    public void reload() {
        reloadConfig();
        this.config = PluginConfig.load(this, getConfig());
        this.manager.applyConfigChange();
    }

    public PluginConfig config() {
        return this.config;
    }

    public EndFightManager manager() {
        return this.manager;
    }

    public DamageTracker tracker() {
        return this.tracker;
    }
}
