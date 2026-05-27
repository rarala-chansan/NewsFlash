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

    private final NewsFlashPlugin plugin;

    public NewsFlashCommand(NewsFlashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("NewsFlash providers: " + plugin.providers().size());
            plugin.providers().forEach(provider -> sender.sendMessage("- " + provider.name() + ": first check after " + provider.initialDelaySeconds() + " second(s), then every " + provider.pollIntervalMinutes() + " minute(s)"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage("NewsFlash reloaded.");
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            plugin.runManualCheck();
            sender.sendMessage("NewsFlash check started.");
            return true;
        }

        return false;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String prefix = args[0].toLowerCase();
        return SUBCOMMANDS.stream()
            .filter(subcommand -> subcommand.startsWith(prefix))
            .toList();
    }
}
