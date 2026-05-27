package dev.newsflash;

import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.config.NewsFlashConfig;
import dev.newsflash.provider.NewsProvider;
import dev.newsflash.provider.mofa.MofaNewsProvider;
import dev.newsflash.provider.p2pquake.P2pQuakeRealtimeProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewsFlashPlugin extends JavaPlugin {
    private NewsFlashConfig pluginConfig;
    private NewsBroadcaster broadcaster;
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

    public void runManualCheck() {
        if (scheduler == null) {
            getLogger().warning("News scheduler is not initialized.");
            return;
        }
        scheduler.runNow(false);
    }

    public NewsFlashConfig pluginConfig() {
        return pluginConfig;
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
        broadcaster = new NewsBroadcaster(this, pluginConfig.broadcastConfig());
        providers = createProviders(pluginConfig);
        scheduler = new NewsScheduler(this, providers, broadcaster);
        scheduler.start();
        p2pQuakeProvider = new P2pQuakeRealtimeProvider(this, pluginConfig.p2pQuakeConfig(), broadcaster);
        p2pQuakeProvider.start();

        getLogger().info("NewsFlash enabled with " + providers.size() + " polling provider(s).");
    }

    private List<NewsProvider> createProviders(NewsFlashConfig config) {
        List<NewsProvider> result = new ArrayList<>();
        if (config.mofaConfig().enabled()) {
            result.add(new MofaNewsProvider(config.mofaConfig(), config.mofaFilterConfig(), getDataFolder().toPath(), getLogger()));
        }

        if (result.isEmpty()) {
            getLogger().log(Level.WARNING, "No news providers are enabled.");
        }
        return List.copyOf(result);
    }
}
