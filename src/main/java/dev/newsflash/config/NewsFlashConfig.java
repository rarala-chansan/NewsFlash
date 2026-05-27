package dev.newsflash.config;

import org.bukkit.configuration.file.FileConfiguration;

public record NewsFlashConfig(
    MofaConfig mofaConfig,
    P2pQuakeConfig p2pQuakeConfig,
    FilterConfig mofaFilterConfig,
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
            new P2pQuakeConfig(
                config.getBoolean("p2pquake.enabled", true),
                config.getString("p2pquake.websocket-url", "wss://api.p2pquake.net/v2/ws"),
                Math.max(1, config.getInt("p2pquake.reconnect-delay-seconds", 10)),
                config.getBoolean("p2pquake.earthquake.enabled", true),
                Math.max(10, config.getInt("p2pquake.earthquake.min-scale", 40)),
                config.getBoolean("p2pquake.earthquake.include-unknown-scale", false),
                Math.max(100, config.getInt("p2pquake.seen-history-limit", 1000))
            ),
            new FilterConfig(
                config.getBoolean("mofa.filter.enabled", config.getBoolean("filter.enabled", true)),
                config.getBoolean("mofa.filter.default-broadcast", config.getBoolean("filter.default-broadcast", false)),
                stringList(config, "mofa.filter.keywords", "filter.keywords").stream()
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

    private static java.util.List<String> stringList(FileConfiguration config, String path, String fallbackPath) {
        java.util.List<String> values = config.getStringList(path);
        if (!values.isEmpty() || config.contains(path)) {
            return values;
        }
        return config.getStringList(fallbackPath);
    }
}
