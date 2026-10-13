package de.larsb6.dragonegg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** {@code /endfight [skip]}: shows the running timer or ends it right away. */
public final class EndFightCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of("skip");

    private final DragonEggPlugin plugin;

    public EndFightCommand(DragonEggPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendRemaining(sender);
            sender.sendMessage(Component.text("/" + label + " skip", NamedTextColor.GRAY));
            return true;
        }
        if (!args[0].toLowerCase(Locale.ROOT).equals("skip")) {
            sender.sendMessage(Component.text("/" + label + " skip", NamedTextColor.RED));
            return true;
        }
        if (this.plugin.manager().skip()) {
            sender.sendMessage(PluginConfig.text(this.plugin.config().msgSkipped()));
        } else {
            sender.sendMessage(PluginConfig.text(this.plugin.config().msgSkipIdle()));
        }
        return true;
    }

    private void sendRemaining(CommandSender sender) {
        EndFightManager manager = this.plugin.manager();
        if (manager.isRunning()) {
            sender.sendMessage(Component.text("Endfight timer: "
                    + EndFightManager.formatTime(manager.remainingSeconds()) + " left", NamedTextColor.YELLOW));
        } else {
            sender.sendMessage(Component.text("Endfight timer: idle", NamedTextColor.GRAY));
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
