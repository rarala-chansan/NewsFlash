package dev.newsflash;

import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.model.NewsItem;
import dev.newsflash.provider.NewsProvider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class NewsScheduler {
    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long TICKS_PER_SECOND = 20L;

    private final NewsFlashPlugin plugin;
    private final List<NewsProvider> providers;
    private NewsBroadcaster broadcaster;
    private final Map<String, BukkitTask> tasks = new HashMap<>();

    public NewsScheduler(NewsFlashPlugin plugin, List<NewsProvider> providers, NewsBroadcaster broadcaster) {
        this.plugin = plugin;
        this.providers = providers;
        this.broadcaster = broadcaster;
    }

    public void start() {
        stop();
        for (NewsProvider provider : providers) {
            startProvider(provider);
        }
    }

    public void stop() {
        for (BukkitTask task : tasks.values()) {
            task.cancel();
        }
        tasks.clear();
    }

    public void runNow(boolean suppressInitialBroadcast) {
        runNow(suppressInitialBroadcast, null);
    }

    public void runNow(boolean suppressInitialBroadcast, Consumer<CheckResult> callback) {
        for (NewsProvider provider : providers) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> poll(provider, suppressInitialBroadcast, callback));
        }
    }

    public boolean runNow(String providerId, boolean suppressInitialBroadcast) {
        return runNow(providerId, suppressInitialBroadcast, null);
    }

    public boolean runNow(String providerId, boolean suppressInitialBroadcast, Consumer<CheckResult> callback) {
        for (NewsProvider provider : providers) {
            if (provider.id().equalsIgnoreCase(providerId)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> poll(provider, suppressInitialBroadcast, callback));
                return true;
            }
        }
        return false;
    }

    public void replaceProvider(NewsProvider provider) {
        removeProvider(provider.id());
        providers.add(provider);
        startProvider(provider);
    }

    public boolean removeProvider(String providerId) {
        BukkitTask task = tasks.remove(providerId.toLowerCase());
        if (task != null) {
            task.cancel();
        }
        return providers.removeIf(provider -> provider.id().equalsIgnoreCase(providerId));
    }

    public List<NewsProvider> providers() {
        return List.copyOf(providers);
    }

    public void broadcaster(NewsBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    private void startProvider(NewsProvider provider) {
        long initialDelayTicks = Math.max(0L, provider.initialDelaySeconds()) * TICKS_PER_SECOND;
        long intervalTicks = Math.max(1L, provider.pollIntervalMinutes()) * TICKS_PER_MINUTE;
        tasks.put(provider.id().toLowerCase(), Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> poll(provider, true, null), initialDelayTicks, intervalTicks));
    }

    private void poll(NewsProvider provider, boolean allowInitialSuppress, Consumer<CheckResult> callback) {
        try {
            List<NewsItem> items = provider.fetchNewItems(allowInitialSuppress);
            if (items.isEmpty()) {
                complete(callback, new CheckResult(provider.id(), provider.name(), 0, true, ""));
                return;
            }

            List<NewsItem> ordered = items.stream()
                .sorted(Comparator.comparing(NewsItem::publishedAt))
                .toList();
            Bukkit.getScheduler().runTask(plugin, () -> {
                broadcaster.broadcast(ordered);
                if (callback != null) {
                    callback.accept(new CheckResult(provider.id(), provider.name(), ordered.size(), true, ""));
                }
            });
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to poll " + provider.name() + ".", exception);
            complete(callback, new CheckResult(provider.id(), provider.name(), 0, false, exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage()));
        }
    }

    private void complete(Consumer<CheckResult> callback, CheckResult result) {
        if (callback != null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        }
    }

    public record CheckResult(
        String providerId,
        String providerName,
        int itemCount,
        boolean success,
        String error
    ) {
    }
}
