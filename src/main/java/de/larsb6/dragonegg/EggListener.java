package de.larsb6.dragonegg;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Keeps the glowing effect on whoever currently carries the egg and the boss bar
 * on whoever is currently in the End. The manager also re-checks everyone twice a
 * second, these events are only there for an instant reaction.
 */
public final class EggListener implements Listener {

    private final DragonEggPlugin plugin;

    public EggListener(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // After a relog the effect is set again for the remaining time.
        syncLater(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.manager().onPlayerQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        this.plugin.manager().onPlayerDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        // Respawning may move the player out of the End, so the bar has to follow.
        syncLater(event.getPlayer(), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        syncLater(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        // A new holder only gets the remaining time, not a fresh ten minutes.
        if (event.getEntity() instanceof Player player) {
            syncLater(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        // Milk clears the effect, so it is set again for the remaining time.
        syncLater(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            syncLater(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            syncLater(player, false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        syncLater(event.getPlayer(), true);
    }

    /** Runs the check one tick later, when the inventory and world are final. */
    private void syncLater(Player player, boolean bossBar) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            this.plugin.manager().syncPlayer(player);
            if (bossBar) {
                this.plugin.manager().refreshBossBar(player);
            }
        });
    }
}
