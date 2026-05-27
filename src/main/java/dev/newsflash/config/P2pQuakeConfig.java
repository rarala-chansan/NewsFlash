package dev.newsflash.config;

import java.util.List;

public record P2pQuakeConfig(
    boolean enabled,
    String websocketUrl,
    int reconnectDelaySeconds,
    boolean earthquakeEnabled,
    int minScale,
    boolean targetPrefecturesEnabled,
    List<String> targetPrefectures,
    boolean includeUnknownScale,
    boolean tsunamiEnabled,
    boolean eewEnabled,
    boolean eewIncludeTests,
    int seenHistoryLimit
) {
}
