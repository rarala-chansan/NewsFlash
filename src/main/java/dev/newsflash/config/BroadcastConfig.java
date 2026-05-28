package dev.newsflash.config;

public record BroadcastConfig(
    String prefix,
    String format,
    boolean console,
    boolean chatEnabled,
    boolean actionBarEnabled,
    String actionBarFormat,
    boolean bossBarEnabled,
    String bossBarFormat,
    String bossBarColor,
    String bossBarOverlay,
    float bossBarProgress,
    int tickerWidth,
    int tickerIntervalTicks,
    int tickerDurationSeconds,
    String tickerSeparator,
    String tickerPadCharacter
) {
}
