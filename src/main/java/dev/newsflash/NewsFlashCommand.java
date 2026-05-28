package dev.newsflash;

import dev.newsflash.i18n.LanguageRegistry;
import java.util.List;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewsFlashCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "check", "status", "language");
    private static final List<String> TARGETS = List.of("mofa", "p2pquake", "rss");
    private static final List<String> LANGUAGE_TARGETS = List.of("default", "personal", "list");
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private final NewsFlashPlugin plugin;

    public NewsFlashCommand(NewsFlashPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            send(sender, plugin.messages(sender).providerCount(plugin.providers().size()));
            plugin.providers().forEach(provider -> send(sender, plugin.messages(sender).providerSchedule(provider.name(), provider.initialDelaySeconds(), provider.pollIntervalMinutes())));
            send(sender, plugin.messages(sender).p2pStatus(
                plugin.pluginConfig().p2pQuakeConfig().enabled(),
                plugin.pluginConfig().p2pQuakeConfig().minScale(),
                plugin.pluginConfig().p2pQuakeConfig().tsunamiEnabled(),
                plugin.pluginConfig().p2pQuakeConfig().eewEnabled()
            ));
            send(sender, plugin.messages(sender).rssStatus(plugin.pluginConfig().rssConfig().enabled(), plugin.pluginConfig().rssConfig().feeds().size()));
            plugin.pluginConfig().rssConfig().feeds().forEach(feed -> send(sender, plugin.messages(sender).rssFeedStatus(feed.id(), feed.enabled(), feed.filterConfig().enabled())));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length == 1) {
                plugin.reloadPlugin();
                send(sender, plugin.messages(sender).reloaded());
                return true;
            }
            if (plugin.reloadTarget(args[1])) {
                send(sender, plugin.messages(sender).reloaded(args[1]));
                return true;
            }
            send(sender, plugin.messages(sender).unknownReloadTarget(args[1]));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (!requireAdmin(sender)) {
                return true;
            }
            if (args.length == 1) {
                plugin.runManualCheck();
                send(sender, plugin.messages(sender).checkStarted());
                return true;
            }
            if (plugin.runManualCheck(args[1])) {
                send(sender, plugin.messages(sender).checkStarted(args[1]));
                return true;
            }
            send(sender, plugin.messages(sender).unknownCheckTarget(args[1]));
            return true;
        }

        if (args[0].equalsIgnoreCase("language")) {
            handleLanguage(sender, args);
            return true;
        }

        return false;
    }

    private void handleLanguage(CommandSender sender, String[] args) {
        if (args.length == 1) {
            send(sender, plugin.messages(sender).languageCurrent(plugin.pluginConfig().language(), plugin.personalLanguage(sender)));
            return;
        }

        if (args[1].equalsIgnoreCase("list")) {
            send(sender, plugin.messages(sender).languageList());
            return;
        }

        if (args[1].equalsIgnoreCase("default")) {
            if (!requireAdmin(sender)) {
                return;
            }
            if (args.length < 3) {
                send(sender, plugin.messages(sender).languageCurrent(plugin.pluginConfig().language(), plugin.personalLanguage(sender)));
                return;
            }
            if (!LanguageRegistry.supported(args[2])) {
                send(sender, plugin.messages(sender).languageUnknown(args[2]));
                return;
            }
            plugin.setDefaultLanguage(args[2]);
            send(sender, plugin.messages(sender).languageDefaultChanged(plugin.pluginConfig().language()));
            return;
        }

        if (args[1].equalsIgnoreCase("personal")) {
            if (args.length < 3) {
                send(sender, plugin.messages(sender).languageCurrent(plugin.pluginConfig().language(), plugin.personalLanguage(sender)));
                return;
            }
            if (args[2].equalsIgnoreCase("default") || args[2].equalsIgnoreCase("clear")) {
                if (!plugin.clearPersonalLanguage(sender)) {
                    send(sender, plugin.messages(sender).languageConsolePersonalUnsupported());
                    return;
                }
                send(sender, plugin.messages(sender).languagePersonalCleared());
                return;
            }
            if (!LanguageRegistry.supported(args[2])) {
                send(sender, plugin.messages(sender).languageUnknown(args[2]));
                return;
            }
            if (!plugin.setPersonalLanguage(sender, args[2])) {
                send(sender, plugin.messages(sender).languageConsolePersonalUnsupported());
                return;
            }
            send(sender, plugin.messages(sender).languagePersonalChanged(LanguageRegistry.normalize(args[2])));
            return;
        }

        send(sender, plugin.messages(sender).languageList());
    }

    private boolean requireAdmin(CommandSender sender) {
        if (sender.hasPermission("newsflash.admin")) {
            return true;
        }
        send(sender, plugin.messages(sender).noPermission());
        return false;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(LEGACY.deserialize(message));
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
        if (args.length == 2 && args[0].equalsIgnoreCase("language")) {
            String prefix = args[1].toLowerCase();
            return LANGUAGE_TARGETS.stream()
                .filter(target -> target.startsWith(prefix))
                .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("language")) {
            if (args[1].equalsIgnoreCase("personal")) {
                String prefix = args[2].toLowerCase();
                return java.util.stream.Stream.concat(LanguageRegistry.SUPPORTED_LANGUAGES.stream(), java.util.stream.Stream.of("default", "clear"))
                    .filter(language -> language.toLowerCase().startsWith(prefix))
                    .toList();
            }
            if (args[1].equalsIgnoreCase("default")) {
                String prefix = args[2].toLowerCase();
                return LanguageRegistry.SUPPORTED_LANGUAGES.stream()
                    .filter(language -> language.toLowerCase().startsWith(prefix))
                    .toList();
            }
        }
        if (args.length != 1) {
            return List.of();
        }
        return List.of();
    }
}
