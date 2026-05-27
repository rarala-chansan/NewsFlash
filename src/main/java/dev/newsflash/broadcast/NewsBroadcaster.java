package dev.newsflash.broadcast;

import dev.newsflash.config.BroadcastConfig;
import dev.newsflash.model.NewsItem;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class NewsBroadcaster {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
        .withZone(ZoneId.systemDefault());

    private final Plugin plugin;
    private final BroadcastConfig config;
    private final MiniMessage miniMessage;

    public NewsBroadcaster(Plugin plugin, BroadcastConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void broadcast(List<NewsItem> items) {
        for (NewsItem item : items) {
            String message = config.format()
                .replace("{prefix}", config.prefix())
                .replace("{source}", escape(item.source()))
                .replace("{type}", escape(item.type()))
                .replace("{title}", escape(item.title()))
                .replace("{lead}", escape(item.lead()))
                .replace("{keyword}", escape(item.matchedKeyword()))
                .replace("{date}", DATE_FORMAT.format(item.publishedAt()))
                .replace("{url}", escapeUrl(item.url()));

            Bukkit.getServer().sendMessage(miniMessage.deserialize(message));
            if (config.console()) {
                plugin.getLogger().info("Broadcasted " + item.source() + " news"
                    + (item.matchedKeyword().isBlank() ? "" : " matched by '" + item.matchedKeyword() + "'")
                    + ": " + item.title() + " " + item.url());
            }
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
