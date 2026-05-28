package dev.newsflash.config;

import dev.newsflash.i18n.LanguageRegistry;
import org.bukkit.configuration.file.FileConfiguration;

public record NewsFlashConfig(
    String language,
    String storageType,
    MofaConfig mofaConfig,
    P2pQuakeConfig p2pQuakeConfig,
    RssConfig rssConfig,
    FilterConfig mofaFilterConfig,
    BroadcastConfig broadcastConfig
) {
    public static NewsFlashConfig from(FileConfiguration config) {
        return new NewsFlashConfig(
            normalizeLanguage(config.getString("translation.language", config.getString("language", "ja"))),
            normalizeStorageType(config.getString("storage.type", "yaml")),
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

    private static String normalizeLanguage(String language) {
        return LanguageRegistry.normalize(language);
    }

    private static String normalizeStorageType(String storageType) {
        if (storageType == null) {
            return "yaml";
        }
        return storageType.equalsIgnoreCase("sqlite") ? "sqlite" : "yaml";
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
            java.util.Map<?, ?> map = maps.get(index);
            java.util.Map<?, ?> filter = mapValue(map, "filter");
            String name = stringValue(map, "name", "RSS Feed " + (index + 1));
            String url = stringValue(map, "url", "");
            String id = stringValue(map, "id", slug(name.isBlank() ? url : name));
            feeds.add(new RssFeedConfig(
                id,
                name,
                url,
                booleanValue(map, "enabled", true),
                new FilterConfig(
                    booleanValue(filter, "enabled", false),
                    stringList(filter, "keywords").stream()
                        .map(String::trim)
                        .filter(keyword -> !keyword.isBlank())
                        .toList()
                )
            ));
        }
        return java.util.List.copyOf(feeds);
    }

    private static java.util.Map<?, ?> mapValue(java.util.Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value instanceof java.util.Map<?, ?> nested) {
            return nested;
        }
        return java.util.Map.of();
    }

    private static String stringValue(java.util.Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        return value.toString();
    }

    private static boolean booleanValue(java.util.Map<?, ?> map, String key, boolean fallback) {
        Object value = map.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private static java.util.List<String> stringList(java.util.Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof java.util.List<?> list)) {
            return java.util.List.of();
        }
        return list.stream()
            .map(Object::toString)
            .toList();
    }

    private static String slug(String value) {
        String slug = value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
        slug = slug.replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "feed" : slug;
    }
}
