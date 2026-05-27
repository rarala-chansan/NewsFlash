package dev.newsflash.config;

public record BroadcastConfig(
    String prefix,
    String format,
    boolean console
) {
}
