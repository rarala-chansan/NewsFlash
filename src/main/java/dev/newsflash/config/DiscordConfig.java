package dev.newsflash.config;

import java.util.List;

public record DiscordConfig(
    boolean enabled,
    String token,
    List<String> channelIds,
    int maxMessageLength,
    int maxMessagesPerMinute,
    boolean ignoreBots,
    boolean stripMentions,
    boolean stripMarkdown,
    boolean broadcastChat,
    boolean broadcastActionBar,
    boolean broadcastBossBar,
    String format
) {
}
