package dev.newsflash;

import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.config.NewsFlashConfig;
import dev.newsflash.i18n.LanguageRegistry;
import dev.newsflash.i18n.NewsFlashMessages;
import dev.newsflash.i18n.PlayerLanguageStore;
import dev.newsflash.i18n.SqlitePlayerLanguageStore;
import dev.newsflash.i18n.YamlPlayerLanguageStore;
import dev.newsflash.model.NewsItem;
import dev.newsflash.provider.NewsProvider;
import dev.newsflash.provider.discord.DiscordRelay;
import dev.newsflash.provider.mofa.MofaNewsProvider;
import dev.newsflash.provider.p2pquake.P2pQuakeRealtimeProvider;
import dev.newsflash.provider.rss.RssNewsProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewsFlashPlugin extends JavaPlugin {
    private NewsFlashConfig pluginConfig;
    private NewsBroadcaster broadcaster;
    private NewsFlashMessages messages;
    private PlayerLanguageStore playerLanguageStore;
    private final Map<String, NewsFlashMessages> messageCache = new HashMap<>();
    private NewsScheduler scheduler;
    private P2pQuakeRealtimeProvider p2pQuakeProvider;
    private DiscordRelay discordRelay;
    private List<NewsProvider> providers;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlugin();

        PluginCommand command = getCommand("newsflash");
        if (command != null) {
            NewsFlashCommand executor = new NewsFlashCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (p2pQuakeProvider != null) {
            p2pQuakeProvider.stop();
        }
        if (discordRelay != null) {
            discordRelay.stop();
        }
        if (broadcaster != null) {
            broadcaster.close();
        }
        if (playerLanguageStore != null) {
            playerLanguageStore.close();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        loadPlugin();
    }

    public boolean reloadTarget(String target) {
        reloadConfig();
        pluginConfig = NewsFlashConfig.from(getConfig());
        configurePlayerLanguageStore(pluginConfig);
        messageCache.clear();
        messages = messages(pluginConfig.language());
        if (broadcaster != null) {
            broadcaster.close();
        }
        broadcaster = new NewsBroadcaster(this, pluginConfig.broadcastConfig());
        scheduler.broadcaster(broadcaster);

        if (target.equalsIgnoreCase("mofa")) {
            if (pluginConfig.mofaConfig().enabled()) {
                scheduler.replaceProvider(new MofaNewsProvider(pluginConfig.mofaConfig(), pluginConfig.mofaFilterConfig(), getDataFolder().toPath(), getLogger(), messages));
            } else {
                scheduler.removeProvider("mofa");
            }
            providers = scheduler.providers();
            return true;
        }

        if (target.equalsIgnoreCase("rss")) {
            if (pluginConfig.rssConfig().enabled()) {
                if (pluginConfig.rssConfig().feeds().isEmpty()) {
                    getLogger().warning("RSS is enabled, but no feeds are configured.");
                }
                RssNewsProvider provider = new RssNewsProvider(pluginConfig.rssConfig(), getDataFolder().toPath(), getLogger());
                validateRssAsync(provider);
                scheduler.replaceProvider(provider);
            } else {
                scheduler.removeProvider("rss");
            }
            providers = scheduler.providers();
            return true;
        }

        if (target.equalsIgnoreCase("p2pquake")) {
            if (p2pQuakeProvider != null) {
                p2pQuakeProvider.stop();
            }
            p2pQuakeProvider = new P2pQuakeRealtimeProvider(this, pluginConfig.p2pQuakeConfig(), broadcaster, messages);
            p2pQuakeProvider.start();
            return true;
        }

        if (target.equalsIgnoreCase("discord")) {
            if (discordRelay != null) {
                discordRelay.stop();
            }
            discordRelay = new DiscordRelay(this, pluginConfig.discordConfig(), broadcaster);
            discordRelay.start();
            return true;
        }

        return false;
    }

    public void runManualCheck() {
        runManualCheck((Consumer<NewsScheduler.CheckResult>) null);
    }

    public void runManualCheck(Consumer<NewsScheduler.CheckResult> callback) {
        if (scheduler == null) {
            getLogger().warning("News scheduler is not initialized.");
            return;
        }
        scheduler.runNow(false, callback);
    }

    public boolean runManualCheck(String target) {
        return runManualCheck(target, null);
    }

    public boolean runManualCheck(String target, Consumer<NewsScheduler.CheckResult> callback) {
        if (scheduler == null) {
            getLogger().warning("News scheduler is not initialized.");
            return false;
        }
        if (target.equalsIgnoreCase("mofa")) {
            return scheduler.runNow("mofa", false, callback);
        }
        if (target.equalsIgnoreCase("rss")) {
            return scheduler.runNow("rss", false, callback);
        }
        return false;
    }

    public void broadcastExample(String title) {
        String normalizedTitle = title == null || title.isBlank()
            ? "日本株がバブルを超えて過去最高値を更新"
            : title;
        broadcaster.broadcast(List.of(new NewsItem(
            "example-" + Instant.now().toEpochMilli(),
            "Example News",
            "サンプル",
            normalizedTitle,
            "NewsFlash の表示確認用サンプル通知です。",
            "https://example.com/newsflash",
            Instant.now(),
            "example"
        )));
    }

    public NewsFlashConfig pluginConfig() {
        return pluginConfig;
    }

    public NewsFlashMessages messages() {
        return messages;
    }

    public NewsFlashMessages messages(String language) {
        String normalized = LanguageRegistry.normalize(language);
        return messageCache.computeIfAbsent(normalized, key -> NewsFlashMessages.load(this, key));
    }

    public NewsFlashMessages messages(CommandSender sender) {
        if (sender instanceof Player player) {
            return playerLanguageStore.language(player)
                .map(this::messages)
                .orElse(messages);
        }
        return messages;
    }

    public String personalLanguage(CommandSender sender) {
        if (sender instanceof Player player) {
            return playerLanguageStore.language(player).orElse("default");
        }
        return "default";
    }

    public boolean setDefaultLanguage(String language) {
        if (!LanguageRegistry.supported(language)) {
            return false;
        }
        String normalized = LanguageRegistry.normalize(language);
        getConfig().set("translation.language", normalized);
        saveConfig();
        reloadPlugin();
        return true;
    }

    public boolean setPersonalLanguage(CommandSender sender, String language) {
        if (!(sender instanceof Player player) || !LanguageRegistry.supported(language)) {
            return false;
        }
        playerLanguageStore.setLanguage(player, language);
        return true;
    }

    public boolean clearPersonalLanguage(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return false;
        }
        playerLanguageStore.clearLanguage(player);
        return true;
    }

    public List<NewsProvider> providers() {
        return providers;
    }

    private void loadPlugin() {
        if (scheduler != null) {
            scheduler.stop();
        }
        if (p2pQuakeProvider != null) {
            p2pQuakeProvider.stop();
            p2pQuakeProvider = null;
        }
        if (discordRelay != null) {
            discordRelay.stop();
            discordRelay = null;
        }
        if (broadcaster != null) {
            broadcaster.close();
        }

        pluginConfig = NewsFlashConfig.from(getConfig());
        configurePlayerLanguageStore(pluginConfig);
        messageCache.clear();
        messages = messages(pluginConfig.language());
        broadcaster = new NewsBroadcaster(this, pluginConfig.broadcastConfig());
        providers = createProviders(pluginConfig);
        scheduler = new NewsScheduler(this, providers, broadcaster);
        scheduler.start();
        p2pQuakeProvider = new P2pQuakeRealtimeProvider(this, pluginConfig.p2pQuakeConfig(), broadcaster, messages);
        p2pQuakeProvider.start();
        discordRelay = new DiscordRelay(this, pluginConfig.discordConfig(), broadcaster);
        discordRelay.start();

        getLogger().info("NewsFlash enabled with " + providers.size() + " polling provider(s).");
    }

    private List<NewsProvider> createProviders(NewsFlashConfig config) {
        List<NewsProvider> result = new ArrayList<>();
        if (config.mofaConfig().enabled()) {
            result.add(new MofaNewsProvider(config.mofaConfig(), config.mofaFilterConfig(), getDataFolder().toPath(), getLogger(), messages));
        }
        if (config.rssConfig().enabled()) {
            if (config.rssConfig().feeds().isEmpty()) {
                getLogger().warning("RSS is enabled, but no feeds are configured.");
            }
            RssNewsProvider provider = new RssNewsProvider(config.rssConfig(), getDataFolder().toPath(), getLogger());
            validateRssAsync(provider);
            result.add(provider);
        }

        if (result.isEmpty()) {
            getLogger().log(Level.WARNING, "No news providers are enabled.");
        }
        return result;
    }

    private void validateRssAsync(RssNewsProvider provider) {
        Bukkit.getScheduler().runTaskAsynchronously(this, provider::validateFeeds);
    }

    private void configurePlayerLanguageStore(NewsFlashConfig config) {
        if (playerLanguageStore != null) {
            playerLanguageStore.close();
        }
        if (config.storageType().equals("sqlite")) {
            playerLanguageStore = new SqlitePlayerLanguageStore(getDataFolder().toPath(), getLogger());
        } else {
            playerLanguageStore = new YamlPlayerLanguageStore(getDataFolder().toPath(), getLogger());
            playerLanguageStore.reload();
        }
    }
}
