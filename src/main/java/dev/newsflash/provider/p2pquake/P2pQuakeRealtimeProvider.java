package dev.newsflash.provider.p2pquake;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.newsflash.NewsFlashPlugin;
import dev.newsflash.broadcast.NewsBroadcaster;
import dev.newsflash.config.P2pQuakeConfig;
import dev.newsflash.i18n.NewsFlashMessages;
import dev.newsflash.model.NewsItem;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public final class P2pQuakeRealtimeProvider implements WebSocket.Listener {
    private static final int JMA_QUAKE_CODE = 551;
    private static final int JMA_TSUNAMI_CODE = 552;
    private static final int EEW_CODE = 556;
    private static final DateTimeFormatter P2PQUAKE_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss[.SSS]");

    private final NewsFlashPlugin plugin;
    private final P2pQuakeConfig config;
    private final NewsBroadcaster broadcaster;
    private final NewsFlashMessages messages;
    private final HttpClient client;
    private final Queue<String> seenIds = new ArrayDeque<>();
    private final Set<String> seenIdSet = new java.util.HashSet<>();
    private final StringBuilder messageBuffer = new StringBuilder();
    private WebSocket webSocket;
    private BukkitTask reconnectTask;
    private volatile boolean stopping;

    public P2pQuakeRealtimeProvider(NewsFlashPlugin plugin, P2pQuakeConfig config, NewsBroadcaster broadcaster, NewsFlashMessages messages) {
        this.plugin = plugin;
        this.config = config;
        this.broadcaster = broadcaster;
        this.messages = messages;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public void start() {
        if (!config.enabled()) {
            return;
        }
        stopping = false;
        connect();
    }

    public void stop() {
        stopping = true;
        if (reconnectTask != null) {
            reconnectTask.cancel();
            reconnectTask = null;
        }
        if (webSocket != null) {
            webSocket.abort();
            webSocket = null;
        }
    }

    private void connect() {
        plugin.getLogger().info("Connecting to P2PQuake WebSocket: " + config.websocketUrl());
        client.newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .buildAsync(URI.create(config.websocketUrl()), this)
            .whenComplete((socket, throwable) -> {
                if (throwable != null) {
                    logConnectionFailure(throwable);
                    scheduleReconnect();
                    return;
                }
                webSocket = socket;
                plugin.getLogger().info("Connected to P2PQuake WebSocket.");
            });
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        WebSocket.Listener.super.onText(webSocket, data, last);
        messageBuffer.append(data);
        if (!last) {
            return null;
        }
        String payload = messageBuffer.toString();
        messageBuffer.setLength(0);
        handleMessage(payload);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        if (!stopping) {
            plugin.getLogger().warning("P2PQuake WebSocket closed: " + statusCode + " " + reason);
            scheduleReconnect();
        }
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        if (!stopping) {
            logConnectionFailure(error);
            scheduleReconnect();
        }
    }

    private void handleMessage(String payload) {
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            int code = intValue(root, "code", -1);
            String id = stringValue(root, "id", "");
            if (id.isBlank() || isSeen(id)) {
                return;
            }

            NewsItem item = switch (code) {
                case JMA_QUAKE_CODE -> handleQuake(root);
                case JMA_TSUNAMI_CODE -> handleTsunami(root);
                case EEW_CODE -> handleEew(root);
                default -> null;
            };

            if (item == null) {
                return;
            }

            remember(id);
            Bukkit.getScheduler().runTask(plugin, () -> broadcaster.broadcast(List.of(item)));
        } catch (Exception exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to handle P2PQuake message.", exception);
        }
    }

    private NewsItem handleQuake(JsonObject root) {
        if (!config.earthquakeEnabled()) {
            return null;
        }

        String id = stringValue(root, "id", "");
        JsonObject earthquake = objectValue(root, "earthquake");
        if (earthquake == null) {
            return null;
        }

        QuakeMatch match = matchQuake(root, earthquake);
        if (!match.shouldBroadcast()) {
            remember(id);
            return null;
        }

        return toQuakeNewsItem(root, earthquake, match);
    }

    private NewsItem handleTsunami(JsonObject root) {
        if (!config.tsunamiEnabled()) {
            return null;
        }
        if (booleanValue(root, "cancelled", false)) {
            remember(stringValue(root, "id", ""));
            return null;
        }

        JsonArray areas = arrayValue(root, "areas");
        if (areas == null || areas.isEmpty()) {
            return null;
        }

        List<String> targets = new ArrayList<>();
        String strongestGrade = "";
        for (JsonElement element : areas) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject area = element.getAsJsonObject();
            String grade = stringValue(area, "grade", "Unknown");
            if (!isTsunamiWarningGrade(grade)) {
                continue;
            }
            strongestGrade = strongerTsunamiGrade(strongestGrade, grade);
            if (targets.size() < 8) {
                targets.add(tsunamiGradeLabel(grade) + " " + stringValue(area, "name", messages.unknownArea()));
            }
        }

        if (targets.isEmpty()) {
            return null;
        }

        String title = messages.tsunamiTitle(tsunamiGradeLabel(strongestGrade));
        String lead = messages.targetAreas(targets);

        return new NewsItem(
            stringValue(root, "id", ""),
            messages.p2pSource(),
            messages.tsunamiType(),
            title,
            lead,
            "https://www.p2pquake.net/",
            parseTime(stringValue(root, "time", "")),
            tsunamiGradeLabel(strongestGrade)
        );
    }

    private NewsItem handleEew(JsonObject root) {
        if (!config.eewEnabled()) {
            return null;
        }
        if (booleanValue(root, "cancelled", false)) {
            remember(stringValue(root, "id", ""));
            return null;
        }
        if (booleanValue(root, "test", false) && !config.eewIncludeTests()) {
            remember(stringValue(root, "id", ""));
            return null;
        }

        JsonObject earthquake = objectValue(root, "earthquake");
        JsonObject hypocenter = earthquake == null ? null : objectValue(earthquake, "hypocenter");
        String hypocenterName = hypocenter == null ? messages.unknownHypocenter() : stringValue(hypocenter, "name", messages.unknownHypocenter());
        double magnitude = hypocenter == null ? -1.0 : doubleValue(hypocenter, "magnitude", -1.0);
        int depth = hypocenter == null ? -1 : (int) doubleValue(hypocenter, "depth", -1.0);
        JsonObject issue = objectValue(root, "issue");
        String serial = issue == null ? messages.unknown() : stringValue(issue, "serial", messages.unknown());

        EewAreaSummary areaSummary = eewAreaSummary(root);
        String title = messages.eewTitle(hypocenterName);
        String lead = messages.eewLead(
            serial,
            scaleLabel(areaSummary.maxScale()),
            magnitude < 0 ? messages.unknown() : String.format("%.1f", magnitude),
            messages.depth(depth),
            areaSummary.text()
        );

        return new NewsItem(
            stringValue(root, "id", ""),
            messages.p2pSource(),
            messages.eewType(),
            title,
            lead,
            "https://www.p2pquake.net/",
            parseTime(stringValue(root, "time", "")),
            messages.eewType()
        );
    }

    private QuakeMatch matchQuake(JsonObject root, JsonObject earthquake) {
        int nationwideMaxScale = intValue(earthquake, "maxScale", -1);
        if (!config.targetPrefecturesEnabled() || config.targetPrefectures().isEmpty()) {
            return new QuakeMatch(shouldBroadcastScale(nationwideMaxScale), nationwideMaxScale, nationwideMaxScale, List.of());
        }

        List<String> matchedAreas = new ArrayList<>();
        int targetMaxScale = -1;
        JsonArray points = arrayValue(root, "points");
        if (points != null) {
            for (JsonElement element : points) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject point = element.getAsJsonObject();
                String pref = stringValue(point, "pref", "");
                int scale = intValue(point, "scale", -1);
                if (!config.targetPrefectures().contains(pref)) {
                    continue;
                }
                targetMaxScale = Math.max(targetMaxScale, scale);
                if (scale >= config.minScale() && matchedAreas.size() < 5) {
                    matchedAreas.add(messages.areaScale(pref, stringValue(point, "addr", ""), scaleLabel(scale)));
                }
            }
        }

        return new QuakeMatch(shouldBroadcastScale(targetMaxScale), nationwideMaxScale, targetMaxScale, List.copyOf(matchedAreas));
    }

    private boolean shouldBroadcastScale(int scale) {
        if (scale < 0) {
            return config.includeUnknownScale();
        }
        return scale >= config.minScale();
    }

    private NewsItem toQuakeNewsItem(JsonObject root, JsonObject earthquake, QuakeMatch match) {
        JsonObject hypocenter = objectValue(earthquake, "hypocenter");
        String hypocenterName = hypocenter == null ? messages.unknownHypocenter() : stringValue(hypocenter, "name", messages.unknownHypocenter());
        double magnitude = hypocenter == null ? -1.0 : doubleValue(hypocenter, "magnitude", -1.0);
        int depth = hypocenter == null ? -1 : intValue(hypocenter, "depth", -1);
        String tsunami = stringValue(earthquake, "domesticTsunami", "Unknown");

        String title = messages.quakeTitle(scaleLabel(match.nationwideMaxScale()), hypocenterName);
        String lead = messages.quakeLead(
            stringValue(earthquake, "time", messages.unknown()),
            magnitude < 0 ? messages.unknown() : String.format("%.1f", magnitude),
            messages.depth(depth),
            tsunamiLabel(tsunami),
            targetAreaText(match)
        );

        return new NewsItem(
            stringValue(root, "id", ""),
            messages.p2pSource(),
            messages.quakeType(),
            title,
            lead,
            "https://www.p2pquake.net/",
            parseTime(stringValue(root, "time", "")),
            matchedKeyword(match)
        );
    }

    private String targetAreaText(QuakeMatch match) {
        if (!config.targetPrefecturesEnabled() || config.targetPrefectures().isEmpty()) {
            return "";
        }
        if (match.matchedAreas().isEmpty()) {
            return messages.targetAreaText(scaleLabel(match.targetMaxScale()));
        }
        return messages.targetAreaText(match.matchedAreas());
    }

    private String matchedKeyword(QuakeMatch match) {
        if (!config.targetPrefecturesEnabled() || config.targetPrefectures().isEmpty()) {
            return messages.matchedMaxScale(scaleLabel(match.nationwideMaxScale()));
        }
        return messages.matchedTargetMaxScale(scaleLabel(match.targetMaxScale()));
    }

    private void scheduleReconnect() {
        if (stopping || reconnectTask != null) {
            return;
        }
        plugin.getLogger().warning("P2PQuake WebSocket will reconnect in " + config.reconnectDelaySeconds() + " second(s).");
        reconnectTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            reconnectTask = null;
            if (!stopping) {
                connect();
            }
        }, config.reconnectDelaySeconds() * 20L);
    }

    private void logConnectionFailure(Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage() == null || cause.getMessage().isBlank()
            ? cause.getClass().getSimpleName()
            : cause.getMessage();
        if (cause instanceof HttpTimeoutException) {
            plugin.getLogger().warning("Failed to connect to P2PQuake WebSocket: timed out.");
            return;
        }
        if (cause instanceof java.io.IOException) {
            plugin.getLogger().warning("P2PQuake WebSocket connection failed: " + message);
            return;
        }
        plugin.getLogger().log(Level.WARNING, "P2PQuake WebSocket error: " + message, cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private boolean isSeen(String id) {
        return seenIdSet.contains(id);
    }

    private void remember(String id) {
        if (!seenIdSet.add(id)) {
            return;
        }
        seenIds.add(id);
        while (seenIds.size() > config.seenHistoryLimit()) {
            String removed = seenIds.poll();
            if (removed != null) {
                seenIdSet.remove(removed);
            }
        }
    }

    private Instant parseTime(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(value, P2PQUAKE_TIME)
                .atZone(ZoneId.of("Asia/Tokyo"))
                .toInstant();
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    private String scaleLabel(int scale) {
        return messages.scaleLabel(scale);
    }

    private String tsunamiLabel(String tsunami) {
        return messages.tsunamiLabel(tsunami);
    }

    private boolean isTsunamiWarningGrade(String grade) {
        return grade.equals("MajorWarning") || grade.equals("Warning") || grade.equals("Watch");
    }

    private String strongerTsunamiGrade(String current, String candidate) {
        if (tsunamiGradeRank(candidate) > tsunamiGradeRank(current)) {
            return candidate;
        }
        return current;
    }

    private int tsunamiGradeRank(String grade) {
        return switch (grade) {
            case "MajorWarning" -> 3;
            case "Warning" -> 2;
            case "Watch" -> 1;
            default -> 0;
        };
    }

    private String tsunamiGradeLabel(String grade) {
        return messages.tsunamiGradeLabel(grade);
    }

    private EewAreaSummary eewAreaSummary(JsonObject root) {
        JsonArray areas = arrayValue(root, "areas");
        if (areas == null || areas.isEmpty()) {
            return new EewAreaSummary(-1, "");
        }

        int maxScale = -1;
        List<String> targets = new ArrayList<>();
        for (JsonElement element : areas) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject area = element.getAsJsonObject();
            int scaleTo = (int) doubleValue(area, "scaleTo", -1.0);
            int scaleFrom = (int) doubleValue(area, "scaleFrom", -1.0);
            int areaScale = Math.max(scaleFrom, scaleTo);
            maxScale = Math.max(maxScale, areaScale);
            if (targets.size() < 8) {
                targets.add(messages.areaScale(stringValue(area, "pref", ""), stringValue(area, "name", ""), scaleLabel(areaScale)));
            }
        }

        if (targets.isEmpty()) {
            return new EewAreaSummary(maxScale, "");
        }
        return new EewAreaSummary(maxScale, messages.targetAreaText(targets));
    }

    private JsonObject objectValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
    }

    private JsonArray arrayValue(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        return element.getAsJsonArray();
    }

    private String stringValue(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsString();
    }

    private int intValue(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsInt();
    }

    private double doubleValue(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsDouble();
    }

    private boolean booleanValue(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        return element.getAsBoolean();
    }

    private record QuakeMatch(
        boolean shouldBroadcast,
        int nationwideMaxScale,
        int targetMaxScale,
        List<String> matchedAreas
    ) {
    }

    private record EewAreaSummary(int maxScale, String text) {
    }
}
