package dev.newsflash;

import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.model.NewsItem;
import dev.newsflash.provider.NewsProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class NewsScheduler {
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long TICKS_PER_SECOND = 20L;

    private final NewsFlashPlugin plugin;
    private final List<NewsProvider> providers;
    private final NewsBroadcaster broadcaster;
    private final List<BukkitTask> tasks = new ArrayList<>();

    public NewsScheduler(NewsFlashPlugin plugin, List<NewsProvider> providers, NewsBroadcaster broadcaster) {
        this.plugin = plugin;
        this.providers = providers;
        this.broadcaster = broadcaster;
    }

    public void start() {
        stop();
        for (NewsProvider provider : providers) {
            long initialDelayTicks = Math.max(0L, provider.initialDelaySeconds()) * TICKS_PER_SECOND;
            long intervalTicks = Math.max(1L, provider.pollIntervalMinutes()) * TICKS_PER_MINUTE;
            tasks.add(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> poll(provider, true), initialDelayTicks, intervalTicks));
        }
    }

    public void stop() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    public void runNow(boolean suppressInitialBroadcast) {
        for (NewsProvider provider : providers) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> poll(provider, suppressInitialBroadcast));
        }
    }

    private void poll(NewsProvider provider, boolean allowInitialSuppress) {
        try {
            List<NewsItem> items = provider.fetchNewItems(allowInitialSuppress);
            if (items.isEmpty()) {
                return;
            }

            List<NewsItem> ordered = items.stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt))
                .toList();
            Bukkit.getScheduler().runTask(plugin, () -> broadcaster.broadcast(ordered));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to poll " + provider.name() + ".", exception);
        }
    }
}
