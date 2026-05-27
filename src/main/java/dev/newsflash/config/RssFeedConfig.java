package dev.newsflash.config;

public record RssFeedConfig(
    String id,
    String name,
    String url,
    boolean enabled,
    FilterConfig filterConfig
) {
}
