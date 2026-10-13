package de.larsb6.dragonegg;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Blocks leaving the End on purpose while the timer runs. Dying and respawning is
 * never touched, so a player may respawn in the overworld as usual.
 *
 * <p>{@link PlayerPortalEvent} has its own handler list, so it needs its own
 * handler next to {@link PlayerTeleportEvent}.
 *
 * <p>A player who has not seen the credits yet does not leave the End through a
 * teleport at all: vanilla shows them the end poem and brings them back through a
 * respawn with {@link PlayerRespawnEvent.RespawnReason#END_PORTAL}. That respawn
 * cannot be cancelled, so its location is pointed back into the End instead.
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.END_PORTAL) {
            // A respawn after death stays untouched, including into the overworld.
            return;
        }
        Player player = event.getPlayer();
        if (!this.plugin.manager().blocksRespawnOutOfEnd(player)) {
            return;
        }
        Location back = this.plugin.manager().lockedReturnLocation();
        if (back == null) {
            return;
        }
        event.setRespawnLocation(back);
        this.plugin.manager().sendLockHint(player);
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
