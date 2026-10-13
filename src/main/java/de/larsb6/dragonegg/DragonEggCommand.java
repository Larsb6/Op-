package de.larsb6.dragonegg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** {@code /dragonegg reload|status|top}. */
public final class DragonEggCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("reload", "status", "top");

    private final DragonEggPlugin plugin;

    public DragonEggCommand(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                this.plugin.reload();
                sender.sendMessage(PluginConfig.text(this.plugin.config().msgReloaded()));
            }
            case "status" -> sendStatus(sender);
            case "top" -> sendTop(sender);
            default -> sender.sendMessage(Component.text("/" + label + " <reload|status|top>", NamedTextColor.RED));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        PluginConfig config = this.plugin.config();
        EndFightManager manager = this.plugin.manager();
        sender.sendMessage(Component.text("DragonEgg", NamedTextColor.GOLD));
        sender.sendMessage(flag("enabled", config.isEnabled()));
        sender.sendMessage(flag("extra-health (x" + config.healthMultiplier() + ")", config.extraHealth()));
        sender.sendMessage(flag("egg-reward", config.eggReward()));
        sender.sendMessage(flag("end-lock", config.endLock()));
        sender.sendMessage(flag("end-lock entry block", config.endLock() && config.blockEntering()));
        sender.sendMessage(flag("glowing", config.glowing()));
        sender.sendMessage(flag("bossbar", config.bossBar()));
        sender.sendMessage(flag("chat-message", config.chatMessage()));
        if (manager.isRunning()) {
            sender.sendMessage(Component.text("Timer: " + EndFightManager.formatTime(manager.remainingSeconds())
                    + " (of " + config.timerSeconds() + "s)", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Timer: idle", NamedTextColor.GRAY));
        }
    }

    private static Component flag(String name, boolean active) {
        return Component.text(name + ": ", NamedTextColor.GRAY)
                .append(Component.text(active ? "on" : "off", active ? NamedTextColor.GREEN : NamedTextColor.RED));
    }

    private void sendTop(CommandSender sender) {
        PluginConfig config = this.plugin.config();
        List<DamageTracker.Entry> ranking = this.plugin.manager().ranking();
        if (ranking.isEmpty()) {
            sender.sendMessage(PluginConfig.text(config.msgTopEmpty()));
            return;
        }
        sender.sendMessage(PluginConfig.text(config.msgTopHeader()));
        int rank = 0;
        for (DamageTracker.Entry entry : ranking) {
            rank++;
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.playerId());
            String name = player.getName() == null ? entry.playerId().toString() : player.getName();
            sender.sendMessage(PluginConfig.text(config.msgTopEntry()
                    .replace("%rank%", Integer.toString(rank))
                    .replace("%player%", name)
                    .replace("%damage%", String.format(Locale.ROOT, "%.1f", entry.damage()))));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String sub : SUB_COMMANDS) {
            if (sub.startsWith(prefix)) {
                matches.add(sub);
            }
        }
        return matches;
    }
}
