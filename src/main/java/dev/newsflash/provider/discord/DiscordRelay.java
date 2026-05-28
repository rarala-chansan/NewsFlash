package dev.newsflash.provider.discord;

import dev.newsflash.NewsFlashPlugin;
import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.config.DiscordConfig;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.Queue;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

public final class DiscordRelay extends ListenerAdapter {
    private static final Pattern MENTION = Pattern.compile("<[@#][!&]?\\d+>");
    private static final Pattern MARKDOWN = Pattern.compile("[*_`~|]");

    private final NewsFlashPlugin plugin;
    private final DiscordConfig config;
    private final NewsBroadcaster broadcaster;
    private final Queue<Instant> recentMessages = new ArrayDeque<>();
    private JDA jda;

    public DiscordRelay(NewsFlashPlugin plugin, DiscordConfig config, NewsBroadcaster broadcaster) {
        this.plugin = plugin;
        this.config = config;
        this.broadcaster = broadcaster;
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        if (config.token().isBlank()) {
            plugin.getLogger().warning("Discord relay is enabled, but discord.token is empty.");
            return;
        }
        if (config.channelIds().isEmpty()) {
            plugin.getLogger().warning("Discord relay is enabled, but discord.channel-ids is empty.");
            return;
        }

        try {
            jda = JDABuilder.createDefault(config.token(), EnumSet.of(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT))
                .addEventListeners(this)
                .build();
            plugin.getLogger().info("Discord relay connecting.");
        } catch (Exception exception) {
            plugin.getLogger().warning("Failed to start Discord relay: " + exception.getMessage());
        }
    }

    public void stop() {
        if (jda != null) {
            jda.shutdownNow();
            jda = null;
        }
        recentMessages.clear();
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!config.channelIds().contains(event.getChannel().getId())) {
            return;
        }
        if (config.ignoreBots() && event.getAuthor().isBot()) {
            return;
        }
        if (!allowMessage()) {
            return;
        }

        String content = clean(event.getMessage());
        if (content.isBlank()) {
            return;
        }
        String author = event.getMember() == null ? event.getAuthor().getName() : event.getMember().getEffectiveName();
        Bukkit.getScheduler().runTask(plugin, () -> broadcaster.broadcastDiscord(
            config.format(),
            author,
            content,
            config.broadcastChat(),
            config.broadcastActionBar(),
            config.broadcastBossBar()
        ));
    }

    private boolean allowMessage() {
        Instant now = Instant.now();
        while (!recentMessages.isEmpty() && recentMessages.peek().isBefore(now.minusSeconds(60))) {
            recentMessages.poll();
        }
        if (recentMessages.size() >= config.maxMessagesPerMinute()) {
            return false;
        }
        recentMessages.add(now);
        return true;
    }

    private String clean(Message message) {
        String content = message.getContentDisplay();
        if (config.stripMentions()) {
            content = MENTION.matcher(content).replaceAll("@mention");
        }
        if (config.stripMarkdown()) {
            content = MARKDOWN.matcher(content).replaceAll("");
        }
        content = content.replaceAll("\\s+", " ").trim();
        if (content.length() > config.maxMessageLength()) {
            return content.substring(0, config.maxMessageLength()) + "...";
        }
        return content;
    }
}
