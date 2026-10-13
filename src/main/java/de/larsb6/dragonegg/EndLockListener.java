package de.larsb6.dragonegg;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Blocks leaving the End on purpose while the timer runs. Dying and respawning is
 * never touched, so a player may respawn in the overworld as usual.
 *
 * <p>{@link PlayerPortalEvent} has its own handler list, so it needs its own
 * handler next to {@link PlayerTeleportEvent}.
 */
public final class EndLockListener implements Listener {

    private final DragonEggPlugin plugin;

    public EndLockListener(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (blocked(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (blocked(event.getPlayer(), event.getFrom(), event.getTo())) {
            event.setCancelled(true);
        }
    }

    private boolean blocked(Player player, Location from, Location to) {
        if (to == null || from == null) {
            return false;
        }
        if (!this.plugin.manager().isLocked(from.getWorld())) {
            return false;
        }
        if (from.getWorld().getUID().equals(to.getWorld().getUID())) {
            // Moving around inside the End, e.g. through an end gateway, stays allowed.
            return false;
        }
        this.plugin.manager().sendLockHint(player);
        return true;
    }
}
