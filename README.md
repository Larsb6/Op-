# DragonEgg

Paper plugin for **Minecraft 1.21.11** (Paper API `1.21.11-R0.1-SNAPSHOT`, Java 21).
No NMS, no reflection, no deprecated APIs.

## Features

1. **Extra health** – When the Ender Dragon spawns, `MAX_HEALTH` is set to the
   vanilla default (200 HP) times `extra-health.multiplier` and the dragon is
   fully healed. The value is always derived from the vanilla default so a
   respawn cannot stack the multiplier.
2. **Damage counting** – The damage every player deals to the dragon is counted.
   Arrows, tridents and other projectiles count for the shooter. Never more is
   counted than the dragon's remaining health.
3. **Egg reward** – When the dragon dies, the player with the most damage
   receives the dragon egg, provided they are online, otherwise rank 2,
   otherwise the next online player in the ranking. If none of them is online,
   the egg drops next to the exit portal. The vanilla egg on the portal is
   removed. If the winner's inventory is full, the egg drops in front of their
   feet. On a tie the player who reached the value first wins.
4. **End lock** – After the dragon died, the End cannot be left on purpose for
   the duration of the timer, and nobody can enter it either
   (`end-lock.block-entering`, on by default): the end portal in the stronghold
   and teleports into the End are cancelled, so only the players who were there
   when the dragon died take part in the endfight. Players already inside are
   never thrown out, and someone who was offline in the End can still log back
   in. Teleports into other worlds are cancelled with a hint in chat, and the
   end portal blocks of the exit portal are taken out of the world
   (`end-lock.unlight-exit-portal`), so the portal only lights up again once the
   timer ended. That also closes the end poem exit, which a
   player who has not seen the credits yet uses instead of a teleport, and which
   therefore cannot be cancelled through a teleport event. Should such a respawn
   still happen, its location is pointed back next to the exit portal.
   Respawning after death is never affected. The removed portal positions are
   stored in the data folder, so a crash during the lock cannot leave the portal
   dark: the next start puts the blocks back.
5. **Glowing** – The current holder of the egg glows, but only for the
   remaining time of the timer. If they die, the glowing is removed and not set
   again after the respawn. If another player picks the egg up, they get the
   remaining time. Once the egg leaves the inventory, the effect ends. After a
   relog or after drinking milk it is set again for the remaining time.
6. **Reset** – All counters are cleared when a new dragon spawns.
7. **Chat message** – A red message is broadcast to everyone as soon as the egg
   has been handed out (`%player%` as placeholder).
8. **ENDFIGHT boss bar** – Red boss bar (`BarColor.RED`, `BarStyle.SOLID`) with
   the title `ENDFIGHT - mm:ss`, shown only to players inside the End. It
   shrinks with the timer, is added when a player enters the End and is removed
   when they leave it.

Features 4, 5 and 8 share **one single timer** that starts when the dragon dies
and is never reset while it runs (`timer.duration-seconds`, default 600). It
never runs while a dragon is alive: spawning a new dragon clears the damage
counters and stops a timer that is still running from the previous kill, so the
boss bar, the glowing and the end lock only ever exist after a dragon died.

## Commands

| Command | Description |
| --- | --- |
| `/dragonegg reload` | Re-read config.yml and apply it immediately |
| `/dragonegg status` | Show the switches and the remaining time |
| `/dragonegg top` | Damage ranking of the last dragon |
| `/endfight` | Show the remaining time of the running timer |
| `/endfight skip` | End the running timer right away |

Permissions: `dragonegg.admin` for `/dragonegg`, `dragonegg.skip` for
`/endfight` (both default: OP).

`/endfight skip` ends the timer exactly as if it had run out: the boss bar and
the glowing disappear, the end lock is lifted and the exit portal lights up
again. The egg stays with whoever holds it. Everyone inside the End is told
about it (`messages.skip-notice`, empty to send nothing).

## Switches

`enabled: false` turns the whole plugin off. In addition every feature has its
own switch: `extra-health.enabled`, `egg-reward.enabled`, `end-lock.enabled`
(with `end-lock.block-entering` and `end-lock.unlight-exit-portal` under it),
`glowing.enabled`, `bossbar.enabled`, `chat-message.enabled`. If a feature (or
the whole plugin) is turned off through `/dragonegg reload` while a timer is
running, it ends immediately.

## Build

```bash
./gradlew build
```

Result: `build/libs/DragonEgg-1.0.0.jar`.
