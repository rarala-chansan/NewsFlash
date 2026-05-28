package dev.newsflash.i18n;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class NewsFlashMessages {
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
        "ja",
        "en",
        "zh_CN",
        "zh_TW",
        "ko",
        "de",
        "fr",
        "es",
        "pt_BR",
        "ru"
    );

    private final YamlConfiguration messages;
    private final YamlConfiguration fallback;

    private NewsFlashMessages(YamlConfiguration messages, YamlConfiguration fallback) {
        this.messages = messages;
        this.fallback = fallback;
    }

    public static NewsFlashMessages load(JavaPlugin plugin, String language) {
        String normalized = normalizeLanguage(language);
        SUPPORTED_LANGUAGES.forEach(supportedLanguage -> ensureLanguageFile(plugin, supportedLanguage));

        YamlConfiguration fallback = loadBundled(plugin, normalized.equals("ja") ? "ja" : "en");
        YamlConfiguration selected = YamlConfiguration.loadConfiguration(plugin.getDataFolder().toPath()
            .resolve("languages")
            .resolve(normalized + ".yml")
            .toFile());
        selected.setDefaults(fallback);
        return new NewsFlashMessages(selected, fallback);
    }

    public String providerCount(int count) {
        return format("commands.provider-count", Map.of("count", count));
    }

    public String providerSchedule(String name, int initialDelaySeconds, int pollIntervalMinutes) {
        return format("commands.provider-schedule", Map.of(
            "name", name,
            "initialDelaySeconds", initialDelaySeconds,
            "pollIntervalMinutes", pollIntervalMinutes
        ));
    }

    public String p2pStatus(boolean enabled, int minScale, boolean tsunamiEnabled, boolean eewEnabled) {
        return format("commands.p2p-status", Map.of(
            "enabled", status(enabled),
            "minScale", minScale,
            "tsunami", status(tsunamiEnabled),
            "eew", status(eewEnabled)
        ));
    }

    public String rssStatus(boolean enabled, int feedCount) {
        return format("commands.rss-status", Map.of("enabled", status(enabled), "feedCount", feedCount));
    }

    public String rssFeedStatus(String id, boolean enabled, boolean filterEnabled) {
        return format("commands.rss-feed-status", Map.of("id", id, "enabled", status(enabled), "filter", status(filterEnabled)));
    }

    public String reloaded() {
        return text("commands.reloaded");
    }

    public String reloaded(String target) {
        return format("commands.reloaded-target", Map.of("target", target));
    }

    public String unknownReloadTarget(String target) {
        return format("commands.unknown-reload-target", Map.of("target", target));
    }

    public String checkStarted() {
        return text("commands.check-started");
    }

    public String checkStarted(String target) {
        return format("commands.check-started-target", Map.of("target", target));
    }

    public String unknownCheckTarget(String target) {
        return format("commands.unknown-check-target", Map.of("target", target));
    }

    public String mofaSource() {
        return text("sources.mofa");
    }

    public String p2pSource() {
        return text("sources.p2pquake");
    }

    public String quakeType() {
        return text("types.quake");
    }

    public String tsunamiType() {
        return text("types.tsunami");
    }

    public String eewType() {
        return text("types.eew");
    }

    public String quakeTitle(String scale, String hypocenter) {
        return format("p2pquake.quake-title", Map.of("scale", scale, "hypocenter", hypocenter));
    }

    public String quakeLead(String time, String magnitude, String depth, String tsunami, String targetAreaText) {
        return format("p2pquake.quake-lead", Map.of(
            "time", time,
            "magnitude", magnitude,
            "depth", depth,
            "tsunami", tsunami,
            "targetAreaText", targetAreaText
        ));
    }

    public String tsunamiTitle(String grade) {
        return format("p2pquake.tsunami-title", Map.of("grade", grade));
    }

    public String targetAreas(List<String> targets) {
        return format("p2pquake.target-areas", Map.of("areas", String.join(", ", targets)));
    }

    public String eewTitle(String hypocenter) {
        return format("p2pquake.eew-title", Map.of("hypocenter", hypocenter));
    }

    public String eewLead(String serial, String scale, String magnitude, String depth, String areaText) {
        return format("p2pquake.eew-lead", Map.of(
            "serial", serial,
            "scale", scale,
            "magnitude", magnitude,
            "depth", depth,
            "areaText", areaText
        ));
    }

    public String targetAreaText(String scale) {
        return format("p2pquake.target-area-max-scale", Map.of("scale", scale));
    }

    public String targetAreaText(List<String> areas) {
        return format("p2pquake.target-area-list", Map.of("areas", String.join(", ", areas)));
    }

    public String matchedMaxScale(String scale) {
        return format("p2pquake.matched-max-scale", Map.of("scale", scale));
    }

    public String matchedTargetMaxScale(String scale) {
        return format("p2pquake.matched-target-max-scale", Map.of("scale", scale));
    }

    public String areaScale(String pref, String area, String scale) {
        return format("p2pquake.area-scale", Map.of("pref", pref, "area", area, "scale", scale));
    }

    public String unknown() {
        return text("common.unknown");
    }

    public String unknownHypocenter() {
        return text("p2pquake.unknown-hypocenter");
    }

    public String unknownArea() {
        return text("p2pquake.unknown-area");
    }

    public String depth(int depth) {
        return depth < 0 ? unknown() : format("p2pquake.depth", Map.of("depth", depth));
    }

    public String scaleLabel(int scale) {
        return text("scales." + scale);
    }

    public String tsunamiLabel(String tsunami) {
        return text("tsunami-status." + tsunami);
    }

    public String tsunamiGradeLabel(String grade) {
        return text("tsunami-grades." + grade);
    }

    private String status(boolean enabled) {
        return text(enabled ? "common.enabled" : "common.disabled");
    }

    private String format(String path, Map<String, ?> values) {
        String message = text(path);
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
        }
        return message;
    }

    private String text(String path) {
        String value = messages.getString(path);
        if (value != null) {
            return value;
        }
        value = fallback.getString(path);
        return value == null ? path : value;
    }

    private static String normalizeLanguage(String language) {
        if (language == null) {
            return "ja";
        }
        for (String supportedLanguage : SUPPORTED_LANGUAGES) {
            if (supportedLanguage.equalsIgnoreCase(language)) {
                return supportedLanguage;
            }
        }
        return "ja";
    }

    private static void ensureLanguageFile(JavaPlugin plugin, String language) {
        String resourcePath = "languages/" + language + ".yml";
        if (!plugin.getDataFolder().toPath().resolve(resourcePath).toFile().exists()) {
            plugin.saveResource(resourcePath, false);
        }
    }

    private static YamlConfiguration loadBundled(JavaPlugin plugin, String language) {
        String resourcePath = "languages/" + language + ".yml";
        try (InputStreamReader reader = new InputStreamReader(plugin.getResource(resourcePath), StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (Exception exception) {
            return new YamlConfiguration();
        }
    }
}
