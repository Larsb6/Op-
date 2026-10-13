package de.larsb6.dragonegg;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.boss.DragonBattle;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Takes the end portal blocks of the exit portal out of the world while the End
 * is locked, so the portal only lights up again once the endfight timer ended.
 *
 * <p>Vanilla lights the portal at the end of the death animation, several seconds
 * after the death event, and it is the only exit that does not go through a
 * teleport event for a player who has not seen the credits yet. Removing the
 * blocks closes that path for everyone, including items and mobs.
 *
 * <p>The removed positions are written to the data folder, so a crash during the
 * lock cannot leave the portal dark forever: the next start puts them back.
 */
final class ExitPortalLock {

    private static final String STATE_FILE = "exit-portal.yml";
    /** The exit portal blocks sit close to the centre of the podium. */
    private static final int RADIUS = 3;
    private static final int DEPTH_BELOW = 2;
    private static final int HEIGHT_ABOVE = 6;

    private final DragonEggPlugin plugin;
    private final Set<BlockKey> removed = new LinkedHashSet<>();
    private UUID worldId;

    ExitPortalLock(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    /** Removes every exit portal block that is currently there and remembers it. */
    void enforce(World world) {
        Location portal = portalLocation(world);
        if (portal == null) {
            return;
        }
        if (this.worldId != null && !this.worldId.equals(world.getUID())) {
            // The fight moved to a different End: hand the old portal back first.
            release();
        }
        boolean changed = false;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                int x = portal.getBlockX() + dx;
                int z = portal.getBlockZ() + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                    // Never force a chunk load just to look for portal blocks.
                    continue;
                }
                for (int dy = -DEPTH_BELOW; dy <= HEIGHT_ABOVE; dy++) {
                    Block block = world.getBlockAt(x, portal.getBlockY() + dy, z);
                    if (block.getType() != Material.END_PORTAL) {
                        continue;
                    }
                    block.setType(Material.AIR, false);
                    this.worldId = world.getUID();
                    changed |= this.removed.add(new BlockKey(x, portal.getBlockY() + dy, z));
                }
            }
        }
        if (changed) {
            saveState();
            this.plugin.getLogger().info("Exit portal closed while the End is locked ("
                    + this.removed.size() + " blocks).");
        }
    }

    /** Puts the remembered blocks back, which lights the portal again. */
    void release() {
        if (this.removed.isEmpty()) {
            // Nothing is closed: only touch the state file if one is still around.
            if (this.worldId != null) {
                this.worldId = null;
                deleteState();
            }
            return;
        }
        World world = this.worldId == null ? null : Bukkit.getWorld(this.worldId);
        if (world == null) {
            this.plugin.getLogger().warning("Cannot light the exit portal again, world "
                    + this.worldId + " is not loaded. The state file is kept for the next start.");
            this.removed.clear();
            this.worldId = null;
            return;
        }
        int restored = 0;
        for (BlockKey key : this.removed) {
            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            // Only fill air back in, so anything placed there in the meantime stays.
            if (block.getType() == Material.AIR) {
                block.setType(Material.END_PORTAL, false);
                restored++;
            }
        }
        this.plugin.getLogger().info("Exit portal lit again (" + restored + " blocks).");
        this.removed.clear();
        this.worldId = null;
        deleteState();
    }

    /** Called on startup to repair a lock that was interrupted by a crash. */
    void restoreFromDisk() {
        File file = stateFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(file);
        String rawWorld = state.getString("world");
        if (rawWorld == null) {
            deleteState();
            return;
        }
        try {
            this.worldId = UUID.fromString(rawWorld);
        } catch (IllegalArgumentException exception) {
            this.plugin.getLogger().warning("Ignoring " + STATE_FILE + ", '" + rawWorld
                    + "' is not a world id.");
            deleteState();
            return;
        }
        for (String raw : state.getStringList("blocks")) {
            BlockKey key = BlockKey.parse(raw);
            if (key == null) {
                this.plugin.getLogger().warning("Ignoring unreadable portal position '" + raw + "'.");
            } else {
                this.removed.add(key);
            }
        }
        this.plugin.getLogger().info("Found an interrupted end lock, lighting the exit portal again.");
        release();
    }

    private Location portalLocation(World world) {
        DragonBattle battle = world.getEnderDragonBattle();
        return battle == null ? null : battle.getEndPortalLocation();
    }

    private File stateFile() {
        return new File(this.plugin.getDataFolder(), STATE_FILE);
    }

    private void saveState() {
        File folder = this.plugin.getDataFolder();
        if (!folder.isDirectory() && !folder.mkdirs()) {
            this.plugin.getLogger().warning("Cannot create " + folder + ", portal state is not saved.");
            return;
        }
        YamlConfiguration state = new YamlConfiguration();
        state.set("world", this.worldId == null ? null : this.worldId.toString());
        List<String> blocks = new ArrayList<>(this.removed.size());
        for (BlockKey key : this.removed) {
            blocks.add(key.serialize());
        }
        state.set("blocks", blocks);
        try {
            state.save(stateFile());
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Cannot write " + STATE_FILE + ": " + exception.getMessage());
        }
    }

    private void deleteState() {
        File file = stateFile();
        if (file.exists() && !file.delete()) {
            this.plugin.getLogger().warning("Cannot delete " + STATE_FILE + ".");
        }
    }

    /** One removed portal block. */
    private record BlockKey(int x, int y, int z) {

        String serialize() {
            return this.x + ";" + this.y + ";" + this.z;
        }

        static BlockKey parse(String raw) {
            String[] parts = raw.split(";");
            if (parts.length != 3) {
                return null;
            }
            try {
                return new BlockKey(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]));
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}
