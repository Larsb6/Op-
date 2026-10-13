package de.larsb6.dragonegg;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/**
 * Holds everything that happens after the dragon died: the one shared timer, the
 * egg hand out, the glowing effect of the current egg holder, the ENDFIGHT boss
 * bar and the end lock.
 *
 * <p>The timer starts when the dragon dies and is never reset while it runs.
 * End lock, glowing and boss bar all read their remaining time from it.
 */
public final class EndFightManager {

    /** How often the manager re-syncs glowing and boss bar (half a second). */
    private static final long TICK_INTERVAL = 10L;
    /** How long a player has to wait for the next "end is locked" hint. */
    private static final long LOCK_MESSAGE_COOLDOWN_MILLIS = 3_000L;
    /** The vanilla egg only appears once the death animation finished, so keep looking. */
    private static final int EGG_REMOVAL_ATTEMPTS = 30;

    private final DragonEggPlugin plugin;
    private final ExitPortalLock portalLock;

    private final Set<UUID> glowingPlayers = new HashSet<>();
    private final Set<UUID> playersInFight = new HashSet<>();
    private final Set<UUID> noRegrowAfterDeath = new HashSet<>();
    private final Map<UUID, Long> lockHintSentAt = new HashMap<>();

    private UUID endWorldId;
    private long startedAt;
    private long endsAt;
    private BossBar bossBar;
    private String bossBarText;
    private BukkitTask ticker;

    public EndFightManager(DragonEggPlugin plugin) {
        this.plugin = plugin;
        this.portalLock = new ExitPortalLock(plugin);
    }

    /** Repairs an end lock that a crash interrupted while the portal was closed. */
    public void restoreExitPortal() {
        this.portalLock.restoreFromDisk();
    }

    // ------------------------------------------------------------------ timer

    public boolean isRunning() {
        return this.endsAt > System.currentTimeMillis();
    }

    public long remainingMillis() {
        return Math.max(0L, this.endsAt - System.currentTimeMillis());
    }

    public long remainingSeconds() {
        return (remainingMillis() + 999L) / 1000L;
    }

    /** The End world the current fight happened in, or {@code null} if no timer runs. */
    public World fightWorld() {
        if (this.endWorldId == null) {
            return null;
        }
        return Bukkit.getWorld(this.endWorldId);
    }

    private void startTimer(World world) {
        PluginConfig config = this.plugin.config();
        this.endWorldId = world.getUID();
        this.startedAt = System.currentTimeMillis();
        this.endsAt = this.startedAt + config.timerSeconds() * 1000L;
        this.noRegrowAfterDeath.clear();
        this.lockHintSentAt.clear();
        clearBossBar();
        if (this.ticker == null) {
            this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, TICK_INTERVAL);
        }
        this.plugin.getLogger().info("Endfight timer started: " + config.timerSeconds() + "s in world "
                + world.getName() + ".");
    }

    /**
     * Ends the running timer right away, exactly as if it had just run out: the
     * boss bar and the glowing go away, the end lock is lifted and the exit portal
     * lights up again. The egg stays with whoever holds it.
     *
     * @return false when no timer was running
     */
    public boolean skip() {
        if (!isRunning()) {
            return false;
        }
        String remaining = formatTime(remainingSeconds());
        World world = fightWorld();
        String notice = this.plugin.config().msgSkipNotice();
        if (world != null && !notice.isBlank()) {
            Component message = PluginConfig.text(notice.replace("%time%", remaining));
            // Collected before stop(), which clears the tracked players.
            for (Player player : world.getPlayers()) {
                player.sendMessage(message);
            }
        }
        this.plugin.getLogger().info("Endfight timer skipped with " + remaining + " left.");
        stop();
        return true;
    }

    /** Stops the timer and removes everything it owns. Used on expiry, reload and disable. */
    public void stop() {
        this.endsAt = 0L;
        this.startedAt = 0L;
        this.endWorldId = null;
        this.lockHintSentAt.clear();
        this.noRegrowAfterDeath.clear();
        this.playersInFight.clear();
        clearBossBar();
        clearGlowing();
        // Giving the portal blocks back is what lights the portal again.
        this.portalLock.release();
        if (this.ticker != null) {
            this.ticker.cancel();
            this.ticker = null;
        }
    }

    /**
     * Re-applies the switches after a reload: a feature that was turned off ends
     * immediately, a changed duration is applied to the running timer.
     */
    public void applyConfigChange() {
        PluginConfig config = this.plugin.config();
        if (!config.isEnabled()) {
            stop();
            return;
        }
        if (this.startedAt > 0L) {
            this.endsAt = this.startedAt + config.timerSeconds() * 1000L;
        }
        if (!isRunning()) {
            stop();
            return;
        }
        if (!config.glowing()) {
            clearGlowing();
        }
        // Dropping the bar makes the next tick rebuild it with the configured colour and style.
        clearBossBar();
        tick();
    }

    // ------------------------------------------------------------------- tick

    private void tick() {
        PluginConfig config = this.plugin.config();
        if (!config.isEnabled() || !isRunning()) {
            stop();
            return;
        }
        updateFightWorldPlayers();
        updateGlowing(config);
        updateBossBar(config);
        updateExitPortal(config);
    }

    /**
     * Remembers who is inside the locked End. The respawn through the exit portal
     * reports no reliable world any more, so the last known one is used there.
     */
    private void updateFightWorldPlayers() {
        this.playersInFight.clear();
        World world = fightWorld();
        if (world == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            this.playersInFight.add(player.getUniqueId());
        }
    }

    private void updateExitPortal(PluginConfig config) {
        World world = fightWorld();
        if (!config.endLock() || !config.unlightExitPortal() || world == null) {
            this.portalLock.release();
            return;
        }
        this.portalLock.enforce(world);
    }

    // ------------------------------------------------------- dragon lifecycle

    /** A new dragon appeared: counters start over and the extra health is applied. */
    public void onDragonSpawn(EnderDragon dragon) {
        this.plugin.tracker().reset();
        // A living dragon means the fight is on again, so anything a previous kill
        // started ends right here. The timer only ever runs between a dragon's
        // death and its expiry, never while a dragon is alive.
        if (isRunning()) {
            this.plugin.getLogger().info("A new dragon spawned, the endfight timer was stopped.");
            stop();
        }
        PluginConfig config = this.plugin.config();
        if (config.extraHealth()) {
            Bukkit.getScheduler().runTask(this.plugin, () -> applyExtraHealth(dragon, config.healthMultiplier()));
        }
    }

    private void applyExtraHealth(EnderDragon dragon, double multiplier) {
        if (!dragon.isValid()) {
            return;
        }
        AttributeInstance attribute = dragon.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            this.plugin.getLogger().warning("The dragon has no MAX_HEALTH attribute, skipping extra health.");
            return;
        }
        // Always derived from the vanilla default so a respawn cannot stack the multiplier.
        double maxHealth = attribute.getDefaultValue() * multiplier;
        attribute.setBaseValue(maxHealth);
        dragon.setHealth(maxHealth);
        this.plugin.getLogger().info("Dragon spawned with " + maxHealth + " HP (x" + multiplier + ").");
    }

    /** The dragon died: hand out the egg, announce it and start the shared timer. */
    public void onDragonDeath(EnderDragon dragon) {
        PluginConfig config = this.plugin.config();
        World world = dragon.getWorld();
        if (config.eggReward()) {
            if (config.removePortalEgg()) {
                scheduleVanillaEggRemoval(world);
            }
            Player winner = findWinner();
            if (winner != null) {
                giveEgg(winner);
                announceWinner(config, winner);
            } else {
                dropEggAtPortal(world, dragon.getLocation());
            }
        }
        if (config.endLock() || config.glowing() || config.bossBar()) {
            startTimer(world);
        }
    }

    /** First player of the damage ranking that is online right now. */
    private Player findWinner() {
        for (DamageTracker.Entry entry : this.plugin.tracker().ranking()) {
            Player player = Bukkit.getPlayer(entry.playerId());
            if (player != null && player.isOnline()) {
                return player;
            }
        }
        return null;
    }

    private void giveEgg(Player winner) {
        Map<Integer, ItemStack> leftover = winner.getInventory().addItem(new ItemStack(Material.DRAGON_EGG));
        for (ItemStack rest : leftover.values()) {
            // Full inventory: the egg lands in front of the winner's feet.
            winner.getWorld().dropItem(winner.getLocation(), rest);
        }
    }

    private void announceWinner(PluginConfig config, Player winner) {
        if (!config.chatMessage()) {
            return;
        }
        Component message = Component.text()
                .color(NamedTextColor.RED)
                .append(PluginConfig.text(config.chatMessageText().replace("%player%", winner.getName())))
                .build();
        Bukkit.broadcast(message);
    }

    private void dropEggAtPortal(World world, Location fallback) {
        world.dropItem(portalDropLocation(world, fallback), new ItemStack(Material.DRAGON_EGG));
    }

    private Location portalDropLocation(World world, Location fallback) {
        Location portal = endPortalLocation(world);
        if (portal == null) {
            return fallback;
        }
        // Beside the portal pillar: an item dropped into the portal block itself
        // would be teleported out of the End.
        return groundNextToPortal(world, portal);
    }

    private static Location groundNextToPortal(World world, Location portal) {
        int x = portal.getBlockX() + 4;
        int z = portal.getBlockZ();
        int y = Math.max(world.getHighestBlockYAt(x, z) + 1, portal.getBlockY() + 1);
        return new Location(world, x + 0.5D, y, z + 0.5D);
    }

    private Location endPortalLocation(World world) {
        DragonBattle battle = world.getEnderDragonBattle();
        return battle == null ? null : battle.getEndPortalLocation();
    }

    /**
     * Vanilla places its own dragon egg on top of the exit portal when the death
     * animation ends, which is several seconds after the death event, so the area
     * is checked repeatedly until the egg shows up.
     */
    private void scheduleVanillaEggRemoval(World world) {
        DragonBattle battle = world.getEnderDragonBattle();
        if (battle == null || battle.getEndPortalLocation() == null) {
            return;
        }
        if (battle.hasBeenPreviouslyKilled()) {
            // Vanilla only places an egg for the first kill of a world, so there is
            // nothing to clean up and no reason to watch the portal area at all.
            return;
        }
        Location portal = battle.getEndPortalLocation();
        new BukkitRunnable() {
            private int attempts;

            @Override
            public void run() {
                this.attempts++;
                if (removeEggBlocks(world, portal) || this.attempts >= EGG_REMOVAL_ATTEMPTS) {
                    cancel();
                }
            }
        }.runTaskTimer(this.plugin, 20L, 20L);
    }

    private boolean removeEggBlocks(World world, Location portal) {
        boolean removed = false;
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -2; y <= 10; y++) {
                    Block block = world.getBlockAt(portal.getBlockX() + x, portal.getBlockY() + y,
                            portal.getBlockZ() + z);
                    if (block.getType() == Material.DRAGON_EGG) {
                        block.setType(Material.AIR, false);
                        removed = true;
                    }
                }
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- glowing

    private void updateGlowing(PluginConfig config) {
        if (!config.glowing()) {
            clearGlowing();
            return;
        }
        int remainingTicks = (int) Math.min(Integer.MAX_VALUE, remainingMillis() / 50L);
        if (remainingTicks <= 0) {
            clearGlowing();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!holdsEgg(player)) {
                // Once the egg is gone the death exemption is no longer needed.
                this.noRegrowAfterDeath.remove(id);
                removeGlowing(player);
            } else if (this.noRegrowAfterDeath.contains(id)) {
                removeGlowing(player);
            } else {
                applyGlowing(player, remainingTicks);
            }
        }
    }

    private void applyGlowing(Player player, int remainingTicks) {
        PotionEffect active = player.getPotionEffect(PotionEffectType.GLOWING);
        // Re-apply when missing or when the remaining time drifted by more than two seconds.
        if (active == null || Math.abs(active.getDuration() - remainingTicks) > 40) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, remainingTicks, 0, false, false, true));
        }
        this.glowingPlayers.add(player.getUniqueId());
    }

    private void removeGlowing(Player player) {
        // Only effects this plugin handed out are taken away again.
        if (this.glowingPlayers.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    /**
     * Potion effects survive a relog, the in-memory bookkeeping does not. A player
     * who logs back in with the egg after the timer ended would keep glowing, so
     * the effect is dropped for an egg carrier even without a bookkeeping entry.
     */
    private void removeLeftoverGlowing(Player player) {
        this.glowingPlayers.remove(player.getUniqueId());
        if (player.getPotionEffect(PotionEffectType.GLOWING) != null && holdsEgg(player)) {
            player.removePotionEffect(PotionEffectType.GLOWING);
        }
    }

    private void clearGlowing() {
        for (UUID id : Set.copyOf(this.glowingPlayers)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.removePotionEffect(PotionEffectType.GLOWING);
            }
        }
        this.glowingPlayers.clear();
    }

    /** True when the player carries the dragon egg somewhere in their inventory. */
    public boolean holdsEgg(Player player) {
        if (player.getItemOnCursor().getType() == Material.DRAGON_EGG) {
            return true;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == Material.DRAGON_EGG) {
                return true;
            }
        }
        return false;
    }

    /** Re-checks a single player, used by the listeners for an immediate reaction. */
    public void syncPlayer(Player player) {
        PluginConfig config = this.plugin.config();
        if (!config.isEnabled() || !config.glowing() || !isRunning()) {
            removeLeftoverGlowing(player);
            return;
        }
        int remainingTicks = (int) Math.min(Integer.MAX_VALUE, remainingMillis() / 50L);
        if (remainingTicks <= 0) {
            removeGlowing(player);
        } else if (!holdsEgg(player)) {
            this.noRegrowAfterDeath.remove(player.getUniqueId());
            removeGlowing(player);
        } else if (!this.noRegrowAfterDeath.contains(player.getUniqueId())) {
            applyGlowing(player, remainingTicks);
        }
    }

    /**
     * The egg holder died: the glowing goes away and is not handed back after the
     * respawn, even if the server keeps the inventory on death.
     */
    public void onPlayerDeath(Player player) {
        if (this.glowingPlayers.contains(player.getUniqueId()) || holdsEgg(player)) {
            this.noRegrowAfterDeath.add(player.getUniqueId());
        }
        removeGlowing(player);
    }

    public void onPlayerQuit(Player player) {
        this.glowingPlayers.remove(player.getUniqueId());
        this.lockHintSentAt.remove(player.getUniqueId());
        if (this.bossBar != null) {
            this.bossBar.removePlayer(player);
        }
    }

    // --------------------------------------------------------------- boss bar

    private void updateBossBar(PluginConfig config) {
        if (!config.bossBar()) {
            clearBossBar();
            return;
        }
        World world = fightWorld();
        if (world == null) {
            clearBossBar();
            return;
        }
        if (this.bossBar == null) {
            this.bossBar = Bukkit.createBossBar(bossBarText(config), config.bossBarColor(), config.bossBarStyle(),
                    new BarFlag[0]);
            this.bossBarText = null;
        }
        String text = bossBarText(config);
        if (!text.equals(this.bossBarText)) {
            this.bossBar.setTitle(text);
            this.bossBarText = text;
        }
        double total = Math.max(1L, config.timerSeconds()) * 1000.0D;
        this.bossBar.setProgress(Math.clamp(remainingMillis() / total, 0.0D, 1.0D));
        this.bossBar.setVisible(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            // Only players that are inside the End of this fight see the bar.
            if (player.getWorld().getUID().equals(world.getUID())) {
                this.bossBar.addPlayer(player);
            } else {
                this.bossBar.removePlayer(player);
            }
        }
    }

    private String bossBarText(PluginConfig config) {
        var builder = Component.text().color(NamedTextColor.RED)
                .append(PluginConfig.text(config.bossBarTitle()));
        if (config.bossBarShowTime()) {
            builder.append(Component.text(" - " + formatTime(remainingSeconds())));
        }
        return LegacyComponentSerializer.legacySection().serialize(builder.build());
    }

    private void clearBossBar() {
        if (this.bossBar != null) {
            this.bossBar.removeAll();
            this.bossBar.setVisible(false);
            this.bossBar = null;
        }
        this.bossBarText = null;
    }

    /** Adds or removes the boss bar for one player, e.g. after a world change. */
    public void refreshBossBar(Player player) {
        PluginConfig config = this.plugin.config();
        if (!config.isEnabled() || !config.bossBar() || !isRunning() || this.bossBar == null) {
            if (this.bossBar != null) {
                this.bossBar.removePlayer(player);
            }
            return;
        }
        World world = fightWorld();
        if (world != null && player.getWorld().getUID().equals(world.getUID())) {
            this.bossBar.addPlayer(player);
        } else {
            this.bossBar.removePlayer(player);
        }
    }

    // --------------------------------------------------------------- end lock

    /** True while the given world is the locked End of the finished fight. */
    public boolean isLocked(World world) {
        return isRunningLock() && this.endWorldId != null && this.endWorldId.equals(world.getUID());
    }

    /**
     * True while the End is locked and the player was inside it a moment ago. The
     * respawn through the exit portal has already taken the player out of the End,
     * so their own world cannot be used to decide this.
     */
    public boolean blocksRespawnOutOfEnd(Player player) {
        return isRunningLock() && this.playersInFight.contains(player.getUniqueId());
    }

    /** A spot on solid ground next to the exit portal, outside the portal itself. */
    public Location lockedReturnLocation() {
        World world = fightWorld();
        if (world == null) {
            return null;
        }
        Location portal = endPortalLocation(world);
        return portal == null ? world.getSpawnLocation() : groundNextToPortal(world, portal);
    }

    private boolean isRunningLock() {
        return this.plugin.config().endLock() && isRunning();
    }

    /** True while nobody may enter the locked End of the finished fight. */
    public boolean blocksEntering(World world) {
        return isRunningLock() && this.plugin.config().blockEntering() && this.endWorldId != null
                && this.endWorldId.equals(world.getUID());
    }

    /** Hint for a player who tries to leave the locked End. */
    public void sendLockHint(Player player) {
        sendHint(player, this.plugin.config().msgEndLocked());
    }

    /** Hint for a player who tries to enter the locked End. */
    public void sendEntryHint(Player player) {
        sendHint(player, this.plugin.config().msgEndClosed());
    }

    /** Throttled, so standing in a portal cannot spam the chat. */
    private void sendHint(Player player, String raw) {
        long now = System.currentTimeMillis();
        Long last = this.lockHintSentAt.get(player.getUniqueId());
        if (last != null && now - last < LOCK_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        this.lockHintSentAt.put(player.getUniqueId(), now);
        player.sendMessage(PluginConfig.text(raw.replace("%time%", formatTime(remainingSeconds()))));
    }

    public static String formatTime(long totalSeconds) {
        long seconds = Math.max(0L, totalSeconds);
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    /** Used by {@code /dragonegg status}. */
    public List<DamageTracker.Entry> ranking() {
        return this.plugin.tracker().ranking();
    }
}
