package de.larsb6.dragonegg;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

/**
 * Immutable snapshot of the config.yml. A new instance is created on startup and
 * on every {@code /dragonegg reload}, so running code never sees half updated values.
 */
public final class PluginConfig {

    private final boolean enabled;
    private final long timerSeconds;

    private final boolean extraHealthEnabled;
    private final double healthMultiplier;

    private final boolean eggRewardEnabled;
    private final boolean removePortalEgg;

    private final boolean endLockEnabled;
    private final boolean glowingEnabled;

    private final boolean bossBarEnabled;
    private final String bossBarTitle;
    private final BarColor bossBarColor;
    private final BarStyle bossBarStyle;
    private final boolean bossBarShowTime;

    private final boolean chatMessageEnabled;
    private final String chatMessageText;

    private final String msgEndLocked;
    private final String msgReloaded;
    private final String msgTopHeader;
    private final String msgTopEntry;
    private final String msgTopEmpty;

    private PluginConfig(Plugin plugin, FileConfiguration cfg) {
        this.enabled = cfg.getBoolean("enabled", true);
        this.timerSeconds = Math.max(1L, cfg.getLong("timer.duration-seconds", 600L));

        this.extraHealthEnabled = cfg.getBoolean("extra-health.enabled", true);
        this.healthMultiplier = Math.max(0.1D, cfg.getDouble("extra-health.multiplier", 10.0D));

        this.eggRewardEnabled = cfg.getBoolean("egg-reward.enabled", true);
        this.removePortalEgg = cfg.getBoolean("egg-reward.remove-portal-egg", true);

        this.endLockEnabled = cfg.getBoolean("end-lock.enabled", true);
        this.glowingEnabled = cfg.getBoolean("glowing.enabled", true);

        this.bossBarEnabled = cfg.getBoolean("bossbar.enabled", true);
        this.bossBarTitle = cfg.getString("bossbar.title", "ENDFIGHT");
        this.bossBarColor = parseColor(plugin, cfg.getString("bossbar.color", "RED"));
        this.bossBarStyle = parseStyle(plugin, cfg.getString("bossbar.style", "SOLID"));
        this.bossBarShowTime = cfg.getBoolean("bossbar.show-time", true);

        this.chatMessageEnabled = cfg.getBoolean("chat-message.enabled", true);
        this.chatMessageText = cfg.getString("chat-message.text", "%player% Has got the dragon egg");

        this.msgEndLocked = cfg.getString("messages.end-locked",
                "&cThe End stays locked for %time% after the dragon fight.");
        this.msgReloaded = cfg.getString("messages.reloaded", "&aDragonEgg: config.yml reloaded.");
        this.msgTopHeader = cfg.getString("messages.top-header", "&6Damage dealt to the Ender Dragon:");
        this.msgTopEntry = cfg.getString("messages.top-entry", "&e%rank%. &f%player% &7- &c%damage% &7HP");
        this.msgTopEmpty = cfg.getString("messages.top-empty", "&7No damage has been counted on the dragon yet.");
    }

    /** Reads the plugin's config.yml from disk into a fresh snapshot. */
    public static PluginConfig load(Plugin plugin, FileConfiguration cfg) {
        return new PluginConfig(plugin, cfg);
    }

    private static BarColor parseColor(Plugin plugin, String raw) {
        String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (BarColor color : BarColor.values()) {
            if (color.name().equals(name)) {
                return color;
            }
        }
        plugin.getLogger().warning("Unknown bossbar.color '" + raw + "', falling back to RED.");
        return BarColor.RED;
    }

    private static BarStyle parseStyle(Plugin plugin, String raw) {
        String name = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        for (BarStyle style : BarStyle.values()) {
            if (style.name().equals(name)) {
                return style;
            }
        }
        plugin.getLogger().warning("Unknown bossbar.style '" + raw + "', falling back to SOLID.");
        return BarStyle.SOLID;
    }

    /** Turns a configured text with '&' colour codes into a component. */
    public static Component text(String raw) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw == null ? "" : raw);
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public long timerSeconds() {
        return this.timerSeconds;
    }

    public boolean extraHealth() {
        return this.enabled && this.extraHealthEnabled;
    }

    public double healthMultiplier() {
        return this.healthMultiplier;
    }

    public boolean eggReward() {
        return this.enabled && this.eggRewardEnabled;
    }

    public boolean removePortalEgg() {
        return this.removePortalEgg;
    }

    public boolean endLock() {
        return this.enabled && this.endLockEnabled;
    }

    public boolean glowing() {
        return this.enabled && this.glowingEnabled;
    }

    public boolean bossBar() {
        return this.enabled && this.bossBarEnabled;
    }

    public String bossBarTitle() {
        return this.bossBarTitle;
    }

    public BarColor bossBarColor() {
        return this.bossBarColor;
    }

    public BarStyle bossBarStyle() {
        return this.bossBarStyle;
    }

    public boolean bossBarShowTime() {
        return this.bossBarShowTime;
    }

    public boolean chatMessage() {
        return this.enabled && this.chatMessageEnabled;
    }

    public String chatMessageText() {
        return this.chatMessageText;
    }

    public String msgEndLocked() {
        return this.msgEndLocked;
    }

    public String msgReloaded() {
        return this.msgReloaded;
    }

    public String msgTopHeader() {
        return this.msgTopHeader;
    }

    public String msgTopEntry() {
        return this.msgTopEntry;
    }

    public String msgTopEmpty() {
        return this.msgTopEmpty;
    }
}
