package dev.newsflash.config;

import org.bukkit.configuration.file.FileConfiguration;

public record NewsFlashConfig(
    MofaConfig mofaConfig,
    FilterConfig filterConfig,
    BroadcastConfig broadcastConfig
) {
    public static NewsFlashConfig from(FileConfiguration config) {
        return new NewsFlashConfig(
            new MofaConfig(
                config.getBoolean("mofa.enabled", true),
                Math.max(0, config.getInt("mofa.initial-delay-seconds", 60)),
                Math.max(1, config.getInt("mofa.poll-interval-minutes", 5)),
                config.getString("mofa.url", "https://www.ezairyu.mofa.go.jp/opendata/area/newarrivalL.xml"),
                Math.max(1, config.getInt("mofa.timeout-seconds", 15)),
                config.getBoolean("mofa.suppress-initial-broadcast", true),
                Math.max(1, config.getInt("mofa.max-broadcast-per-poll", 5)),
                Math.max(100, config.getInt("mofa.seen-history-limit", 1000))
            ),
            new FilterConfig(
                config.getBoolean("filter.enabled", true),
                config.getBoolean("filter.default-broadcast", false),
                config.getStringList("filter.keywords").stream()
                    .map(String::trim)
                    .filter(keyword -> !keyword.isBlank())
                    .toList()
            ),
            new BroadcastConfig(
                config.getString("broadcast.prefix", "<red><bold>[NewsFlash]</bold></red>"),
                config.getString("broadcast.format", "{prefix} <gold>{source}</gold> <yellow>{title}</yellow> <gray>({date})</gray> <aqua><click:open_url:'{url}'>{url}</click></aqua>"),
                config.getBoolean("broadcast.console", true)
            )
        );
    }
}
