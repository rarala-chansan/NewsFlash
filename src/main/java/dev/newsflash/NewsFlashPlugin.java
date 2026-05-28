package dev.newsflash;

import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.config.NewsFlashConfig;
import dev.newsflash.i18n.NewsFlashMessages;
import dev.newsflash.provider.NewsProvider;
import dev.newsflash.provider.mofa.MofaNewsProvider;
import dev.newsflash.provider.p2pquake.P2pQuakeRealtimeProvider;
import dev.newsflash.provider.rss.RssNewsProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewsFlashPlugin extends JavaPlugin {
    private NewsFlashConfig pluginConfig;
    private NewsBroadcaster broadcaster;
    private NewsFlashMessages messages;
    private NewsScheduler scheduler;
    private P2pQuakeRealtimeProvider p2pQuakeProvider;
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
    }

    public void reloadPlugin() {
        reloadConfig();
        loadPlugin();
    }

    public boolean reloadTarget(String target) {
        reloadConfig();
        pluginConfig = NewsFlashConfig.from(getConfig());
        messages = new NewsFlashMessages(pluginConfig.language());
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

        return false;
    }

    public void runManualCheck() {
        if (scheduler == null) {
            getLogger().warning("News scheduler is not initialized.");
            return;
        }
        scheduler.runNow(false);
    }

    public boolean runManualCheck(String target) {
        if (scheduler == null) {
            getLogger().warning("News scheduler is not initialized.");
            return false;
        }
        if (target.equalsIgnoreCase("mofa")) {
            return scheduler.runNow("mofa", false);
        }
        if (target.equalsIgnoreCase("rss")) {
            return scheduler.runNow("rss", false);
        }
        return false;
    }

    public NewsFlashConfig pluginConfig() {
        return pluginConfig;
    }

    public NewsFlashMessages messages() {
        return messages;
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

        pluginConfig = NewsFlashConfig.from(getConfig());
        messages = new NewsFlashMessages(pluginConfig.language());
        broadcaster = new NewsBroadcaster(this, pluginConfig.broadcastConfig());
        providers = createProviders(pluginConfig);
        scheduler = new NewsScheduler(this, providers, broadcaster);
        scheduler.start();
        p2pQuakeProvider = new P2pQuakeRealtimeProvider(this, pluginConfig.p2pQuakeConfig(), broadcaster, messages);
        p2pQuakeProvider.start();

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
}
