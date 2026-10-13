package de.larsb6.dragonegg;

import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.EnderDragonPart;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Spawn, damage counting and death of the Ender Dragon. */
public final class DragonListener implements Listener {

    private final DragonEggPlugin plugin;

    public DragonListener(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawn(EntitySpawnEvent event) {
        if (!this.plugin.config().isEnabled()) {
            return;
        }
        if (event.getEntity() instanceof EnderDragon dragon) {
            this.plugin.manager().onDragonSpawn(dragon);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!this.plugin.config().isEnabled()) {
            return;
        }
        EnderDragon dragon = resolveDragon(event.getEntity());
        if (dragon == null) {
            return;
        }
        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        // The event has not been applied yet, so getHealth() is still the health
        // before this hit: never count more than what is actually left.
        double counted = Math.min(event.getFinalDamage(), dragon.getHealth());
        this.plugin.tracker().add(attacker.getUniqueId(), counted);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        if (!this.plugin.config().isEnabled()) {
            return;
        }
        if (event.getEntity() instanceof EnderDragon dragon) {
            this.plugin.manager().onDragonDeath(dragon);
        }
    }

    /** Hits can land on the dragon itself or on one of its body parts. */
    private static EnderDragon resolveDragon(Entity entity) {
        if (entity instanceof EnderDragon dragon) {
            return dragon;
        }
        if (entity instanceof EnderDragonPart part) {
            return part.getParent();
        }
        return null;
    }

    /** Arrows, tridents and other projectiles count for the player that shot them. */
    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }
}
