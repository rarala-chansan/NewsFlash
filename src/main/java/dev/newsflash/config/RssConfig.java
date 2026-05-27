package dev.newsflash.config;

import java.util.List;

public record RssConfig(
    boolean enabled,
    int initialDelaySeconds,
    int pollIntervalMinutes,
    int timeoutSeconds,
    int maxBroadcastPerPoll,
    int seenHistoryLimit,
    List<RssFeedConfig> feeds
) {
}
