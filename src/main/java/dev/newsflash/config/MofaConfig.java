package dev.newsflash.config;

public record MofaConfig(
    boolean enabled,
    int initialDelaySeconds,
    int pollIntervalMinutes,
    String url,
    int timeoutSeconds,
    boolean suppressInitialBroadcast,
    int maxBroadcastPerPoll,
    int seenHistoryLimit
) {
}
