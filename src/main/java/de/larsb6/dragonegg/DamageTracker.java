package de.larsb6.dragonegg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Counts the damage every player has dealt to the current Ender Dragon.
 *
 * <p>The counters are cleared whenever a new dragon spawns. A player's total is
 * only ever increased, so the moment a player last gained damage is also the
 * moment they reached their current value: on a tie the smaller sequence number
 * wins, which is the player that reached that value first.
 */
public final class DamageTracker {

    private final Map<UUID, Double> damage = new HashMap<>();
    private final Map<UUID, Long> reachedAt = new HashMap<>();
    private long sequence;

    /** Adds damage for a player. Values of zero or less are ignored. */
    public void add(UUID playerId, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        this.damage.merge(playerId, amount, Double::sum);
        this.reachedAt.put(playerId, ++this.sequence);
    }

    /** Clears all counters, used when a new dragon spawns. */
    public void reset() {
        this.damage.clear();
        this.reachedAt.clear();
        this.sequence = 0L;
    }

    public boolean isEmpty() {
        return this.damage.isEmpty();
    }

    /** All players that dealt damage, highest damage first, earliest first on a tie. */
    public List<Entry> ranking() {
        List<Entry> entries = new ArrayList<>(this.damage.size());
        for (Map.Entry<UUID, Double> entry : this.damage.entrySet()) {
            entries.add(new Entry(entry.getKey(), entry.getValue(),
                    this.reachedAt.getOrDefault(entry.getKey(), Long.MAX_VALUE)));
        }
        entries.sort(Comparator.comparingDouble(Entry::damage).reversed()
                .thenComparingLong(Entry::reachedAt));
        return entries;
    }

    /** One player's place in the ranking. */
    public record Entry(UUID playerId, double damage, long reachedAt) {
    }
}
