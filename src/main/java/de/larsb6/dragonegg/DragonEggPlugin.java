package de.larsb6.dragonegg;

import org.bukkit.command.PluginCommand;
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

        getServer().getPluginManager().registerEvents(new DragonListener(this), this);
        getServer().getPluginManager().registerEvents(new EggListener(this), this);
        getServer().getPluginManager().registerEvents(new EndLockListener(this), this);

        PluginCommand command = getCommand("dragonegg");
        if (command == null) {
            getLogger().severe("Befehl 'dragonegg' fehlt in der plugin.yml.");
        } else {
            DragonEggCommand executor = new DragonEggCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        if (!this.config.isEnabled()) {
            getLogger().info("DragonEgg ist in der config.yml deaktiviert (enabled: false).");
        }
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
