package dev.newsflash;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewsFlashCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "check", "status");
    private static final List<String> TARGETS = List.of("mofa", "p2pquake", "rss");

    private final NewsFlashPlugin plugin;

    public NewsFlashCommand(NewsFlashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(plugin.messages().providerCount(plugin.providers().size()));
            plugin.providers().forEach(provider -> sender.sendMessage(plugin.messages().providerSchedule(provider.name(), provider.initialDelaySeconds(), provider.pollIntervalMinutes())));
            sender.sendMessage(plugin.messages().p2pStatus(
                plugin.pluginConfig().p2pQuakeConfig().enabled(),
                plugin.pluginConfig().p2pQuakeConfig().minScale(),
                plugin.pluginConfig().p2pQuakeConfig().tsunamiEnabled(),
                plugin.pluginConfig().p2pQuakeConfig().eewEnabled()
            ));
            sender.sendMessage(plugin.messages().rssStatus(plugin.pluginConfig().rssConfig().enabled(), plugin.pluginConfig().rssConfig().feeds().size()));
            plugin.pluginConfig().rssConfig().feeds().forEach(feed -> sender.sendMessage(plugin.messages().rssFeedStatus(feed.id(), feed.enabled(), feed.filterConfig().enabled())));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (args.length == 1) {
                plugin.reloadPlugin();
                sender.sendMessage(plugin.messages().reloaded());
                return true;
            }
            if (plugin.reloadTarget(args[1])) {
                sender.sendMessage(plugin.messages().reloaded(args[1]));
                return true;
            }
            sender.sendMessage(plugin.messages().unknownReloadTarget(args[1]));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (args.length == 1) {
                plugin.runManualCheck();
                sender.sendMessage(plugin.messages().checkStarted());
                return true;
            }
            if (plugin.runManualCheck(args[1])) {
                sender.sendMessage(plugin.messages().checkStarted(args[1]));
                return true;
            }
            sender.sendMessage(plugin.messages().unknownCheckTarget(args[1]));
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return SUBCOMMANDS.stream()
                .filter(subcommand -> subcommand.startsWith(prefix))
                .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("reload") || args[0].equalsIgnoreCase("check"))) {
            String prefix = args[1].toLowerCase();
            return TARGETS.stream()
                .filter(target -> target.startsWith(prefix))
                .toList();
        }
        if (args.length != 1) {
            return List.of();
        }
        return List.of();
    }
}
