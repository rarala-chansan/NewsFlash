package dev.newsflash.config;

import org.bukkit.configuration.file.FileConfiguration;

public record NewsFlashConfig(
    MofaConfig mofaConfig,
    P2pQuakeConfig p2pQuakeConfig,
    RssConfig rssConfig,
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
                config.getBoolean("p2pquake.earthquake.target-prefectures.enabled", false),
                stringList(config, "p2pquake.earthquake.target-prefectures.list", "p2pquake.earthquake.target-prefectures").stream()
                    .map(String::trim)
                    .filter(prefecture -> !prefecture.isBlank())
                    .toList(),
                config.getBoolean("p2pquake.earthquake.include-unknown-scale", false),
                config.getBoolean("p2pquake.tsunami.enabled", true),
                config.getBoolean("p2pquake.eew.enabled", true),
                config.getBoolean("p2pquake.eew.include-tests", false),
                Math.max(100, config.getInt("p2pquake.seen-history-limit", 1000))
            ),
            new RssConfig(
                config.getBoolean("rss.enabled", false),
                Math.max(0, config.getInt("rss.initial-delay-seconds", 60)),
                Math.max(1, config.getInt("rss.poll-interval-minutes", 10)),
                Math.max(1, config.getInt("rss.timeout-seconds", 15)),
                Math.max(1, config.getInt("rss.max-broadcast-per-poll", 5)),
                Math.max(100, config.getInt("rss.seen-history-limit", 1000)),
                rssFeeds(config)
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

    private static java.util.List<RssFeedConfig> rssFeeds(FileConfiguration config) {
        java.util.List<java.util.Map<?, ?>> maps = config.getMapList("rss.feeds");
        java.util.List<RssFeedConfig> feeds = new java.util.ArrayList<>();
        for (int index = 0; index < maps.size(); index++) {
            String path = "rss.feeds." + index;
            String name = config.getString(path + ".name", "RSS Feed " + (index + 1));
            String url = config.getString(path + ".url", "");
            String id = config.getString(path + ".id", slug(name.isBlank() ? url : name));
            feeds.add(new RssFeedConfig(
                id,
                name,
                url,
                config.getBoolean(path + ".enabled", true),
                new FilterConfig(
                    config.getBoolean(path + ".filter.enabled", false),
                    config.getBoolean(path + ".filter.default-broadcast", true),
                    config.getStringList(path + ".filter.keywords").stream()
                        .map(String::trim)
                        .filter(keyword -> !keyword.isBlank())
                        .toList()
                )
            ));
        }
        return java.util.List.copyOf(feeds);
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "feed" : slug;
    }
}
