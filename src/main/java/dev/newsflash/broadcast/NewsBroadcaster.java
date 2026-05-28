package dev.newsflash.broadcast;

import dev.newsflash.config.BroadcastConfig;
import dev.newsflash.model.NewsItem;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public final class NewsBroadcaster {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final BroadcastConfig config;
    private final MiniMessage miniMessage;
    private final Deque<NewsItem> tickerQueue = new ArrayDeque<>();
    private BukkitTask tickerTask;
    private BossBar activeBossBar;

    public NewsBroadcaster(Plugin plugin, BroadcastConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void broadcast(List<NewsItem> items) {
        for (NewsItem item : items) {
            if (config.chatEnabled()) {
                Bukkit.getServer().sendMessage(miniMessage.deserialize(format(config.format(), item, "")));
            }
            if (config.actionBarEnabled() || config.bossBarEnabled()) {
                enqueueTicker(item);
            }
            if (config.console()) {
                plugin.getLogger().info("Broadcasted " + item.source() + " news"
                    + (item.matchedKeyword().isBlank() ? "" : " matched by '" + item.matchedKeyword() + "'")
                    + ": " + item.title() + " " + item.url());
            }
        }
    }

    public void broadcastDiscord(String format, String author, String message, boolean chat, boolean actionBar, boolean bossBar) {
        Component component = miniMessage.deserialize(format, TagResolver.resolver(
            Placeholder.parsed("author", escape(author)),
            Placeholder.parsed("message", escape(message))
        ));
        if (chat) {
            Bukkit.getServer().sendMessage(component);
        }
        if (actionBar) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendActionBar(component);
            }
        }
        if (bossBar) {
            BossBar bossBarMessage = BossBar.bossBar(component, config.bossBarProgress(), bossBarColor(), bossBarOverlay());
            showBossBar(bossBarMessage);
            Bukkit.getScheduler().runTaskLater(plugin, () -> hideBossBar(bossBarMessage), config.tickerDurationSeconds() * 20L);
        }
    }

    public void close() {
        tickerQueue.clear();
        if (tickerTask != null) {
            tickerTask.cancel();
            tickerTask = null;
        }
        if (activeBossBar != null) {
            hideBossBar(activeBossBar);
            activeBossBar = null;
        }
    }

    private void enqueueTicker(NewsItem item) {
        tickerQueue.addLast(item);
        if (tickerTask == null || tickerTask.isCancelled()) {
            playNextTicker();
        }
    }

    private void playNextTicker() {
        NewsItem item = tickerQueue.pollFirst();
        if (item == null) {
            tickerTask = null;
            return;
        }

        int width = config.tickerWidth();
        int intervalTicks = config.tickerIntervalTicks();
        int totalFrames = Math.max(1, config.tickerDurationSeconds() * 20 / intervalTicks);
        String padCharacter = tickerPadCharacter();
        String cycle = pad(padCharacter, width) + tickerText(item) + config.tickerSeparator();
        if (config.bossBarEnabled()) {
            activeBossBar = BossBar.bossBar(Component.empty(), config.bossBarProgress(), bossBarColor(), bossBarOverlay());
            showBossBar(activeBossBar);
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            private int frame;

            @Override
            public void run() {
                if (frame >= totalFrames) {
                    if (activeBossBar != null) {
                        hideBossBar(activeBossBar);
                        activeBossBar = null;
                    }
                    cancel();
                    tickerTask = null;
                    playNextTicker();
                    return;
                }

                String ticker = tickerFrame(cycle, frame, width, padCharacter);
                if (config.actionBarEnabled()) {
                    Component actionBar = miniMessage.deserialize(format(config.actionBarFormat(), item, ticker));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.sendActionBar(actionBar);
                    }
                }
                if (activeBossBar != null) {
                    activeBossBar.name(miniMessage.deserialize(format(config.bossBarFormat(), item, ticker)));
                }
                frame++;
            }
        };
        tickerTask = runnable.runTaskTimer(plugin, 0L, intervalTicks);
    }

    private String format(String template, NewsItem item, String ticker) {
        return template
            .replace("{prefix}", config.prefix())
            .replace("{source}", escape(item.source()))
            .replace("{type}", escape(item.type()))
            .replace("{title}", escape(item.title()))
            .replace("{lead}", escape(item.lead()))
            .replace("{keyword}", escape(item.matchedKeyword()))
            .replace("{date}", DATE_FORMAT.format(item.publishedAt()))
            .replace("{url}", escapeUrl(item.url()))
            .replace("{ticker}", escape(ticker));
    }

    private String tickerText(NewsItem item) {
        return item.source() + " " + item.title() + " (" + DATE_FORMAT.format(item.publishedAt()) + ")";
    }

    private String tickerFrame(String cycle, int frame, int width, String padCharacter) {
        int[] codePoints = cycle.codePoints().toArray();
        int offset = frame % codePoints.length;
        StringBuilder result = new StringBuilder();
        int currentWidth = 0;
        int index = offset;
        while (currentWidth < width) {
            int codePoint = codePoints[index];
            int codePointWidth = displayWidth(codePoint);
            if (currentWidth + codePointWidth > width) {
                break;
            }
            result.appendCodePoint(codePoint);
            currentWidth += codePointWidth;
            index = (index + 1) % codePoints.length;
        }
        return padToWidth(result.toString(), width, padCharacter);
    }

    private String tickerPadCharacter() {
        String value = config.tickerPadCharacter();
        if (value == null || value.isEmpty() || value.isBlank()) {
            return "\u00A0";
        }
        return new String(Character.toChars(value.codePointAt(0)));
    }

    private String pad(String padCharacter, int width) {
        return padToWidth("", width, padCharacter);
    }

    private String padToWidth(String value, int width, String padCharacter) {
        StringBuilder result = new StringBuilder(value);
        int currentWidth = displayWidth(value);
        int padWidth = Math.max(1, displayWidth(padCharacter));
        while (currentWidth + padWidth <= width) {
            result.append(padCharacter);
            currentWidth += padWidth;
        }
        while (currentWidth < width) {
            result.append(' ');
            currentWidth++;
        }
        return result.toString();
    }

    private int displayWidth(String value) {
        return value.codePoints()
            .map(this::displayWidth)
            .sum();
    }

    private int displayWidth(int codePoint) {
        if (Character.isISOControl(codePoint)) {
            return 0;
        }
        if (codePoint == 0x00A0) {
            return 1;
        }
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HIRAGANA
            || block == Character.UnicodeBlock.KATAKANA
            || block == Character.UnicodeBlock.HANGUL_SYLLABLES
            || block == Character.UnicodeBlock.HANGUL_JAMO
            || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS) {
            return 2;
        }
        return 1;
    }

    private void showBossBar(BossBar bossBar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showBossBar(bossBar);
        }
    }

    private void hideBossBar(BossBar bossBar) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
    }

    private BossBar.Color bossBarColor() {
        try {
            return BossBar.Color.valueOf(config.bossBarColor().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BossBar.Color.WHITE;
        }
    }

    private BossBar.Overlay bossBarOverlay() {
        try {
            return BossBar.Overlay.valueOf(config.bossBarOverlay().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
            .replace("<", "\\<");
    }

    private String escapeUrl(String value) {
        if (value == null || value.isBlank()) {
            return "https://www.anzen.mofa.go.jp/";
        }
        return value.replace("'", "%27").replace(" ", "%20");
    }
}
