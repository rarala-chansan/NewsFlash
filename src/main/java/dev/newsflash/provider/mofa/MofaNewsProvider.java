package dev.newsflash.provider.mofa;

import dev.newsflash.config.MofaConfig;
import dev.newsflash.config.FilterConfig;
import dev.newsflash.model.NewsItem;
import dev.newsflash.provider.NewsProvider;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class MofaNewsProvider implements NewsProvider {
    private static final DateTimeFormatter MOFA_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    private final MofaConfig config;
    private final FilterConfig filterConfig;
    private final Path seenFile;
    private final Logger logger;
    private final HttpClient client;
    private final Set<String> seenIds;
    private boolean initialized;

    public MofaNewsProvider(MofaConfig config, FilterConfig filterConfig, Path dataFolder, Logger logger) {
        this.config = config;
        this.filterConfig = filterConfig;
        this.seenFile = dataFolder.resolve("mofa-seen.txt");
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
            .build();
        this.seenIds = loadSeenIds();
        this.initialized = !seenIds.isEmpty();
    }

    @Override
    public String name() {
        return "MOFA Overseas Safety";
    }

    @Override
    public int initialDelaySeconds() {
        return config.initialDelaySeconds();
    }

    @Override
    public int pollIntervalMinutes() {
        return config.pollIntervalMinutes();
    }

    @Override
    public List<NewsItem> fetchNewItems(boolean allowInitialSuppress) throws Exception {
        List<NewsItem> fetched = fetchItems();
        List<NewsItem> newItems = fetched.stream()
            .filter(item -> !seenIds.contains(item.id()))
            .map(this::applyFilter)
            .filter(item -> item != null)
            .limit(config.maxBroadcastPerPoll())
            .toList();

        fetched.forEach(item -> seenIds.add(item.id()));
        saveSeenIds();

        if (!initialized && allowInitialSuppress && config.suppressInitialBroadcast()) {
            initialized = true;
            logger.info("MOFA feed initialized with " + fetched.size() + " item(s); initial broadcast suppressed.");
            return List.of();
        }

        initialized = true;
        logger.info("MOFA feed fetched " + fetched.size() + " item(s); " + newItems.size() + " item(s) matched notification filter.");
        return newItems;
    }

    private List<NewsItem> fetchItems() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.url()))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("User-Agent", "NewsFlash Minecraft Plugin")
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected MOFA response status: " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            return parse(body);
        }
    }

    private List<NewsItem> parse(InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(inputStream);
        NodeList mails = document.getElementsByTagName("mail");
        List<NewsItem> items = new ArrayList<>();

        for (int index = 0; index < mails.getLength(); index++) {
            Element mail = (Element) mails.item(index);
            String keyCd = text(mail, "keyCd");
            if (keyCd.isBlank()) {
                continue;
            }

            items.add(new NewsItem(
                keyCd,
                "外務省 海外安全情報",
                text(mail, "infoNameLong", text(mail, "infoName", text(mail, "infoType"))),
                text(mail, "title"),
                text(mail, "lead"),
                text(mail, "infoUrl"),
                parseDate(text(mail, "leaveDate")),
                ""
            ));
        }

        return items;
    }

    private NewsItem applyFilter(NewsItem item) {
        if (!filterConfig.enabled()) {
            return item;
        }

        String haystack = (item.title() + "\n" + item.lead() + "\n" + item.type()).toLowerCase();
        for (String keyword : filterConfig.keywords()) {
            if (haystack.contains(keyword.toLowerCase())) {
                return withMatchedKeyword(item, keyword);
            }
        }

        if (filterConfig.defaultBroadcast()) {
            return item;
        }
        return null;
    }

    private NewsItem withMatchedKeyword(NewsItem item, String matchedKeyword) {
        return new NewsItem(
            item.id(),
            item.source(),
            item.type(),
            item.title(),
            item.lead(),
            item.url(),
            item.publishedAt(),
            matchedKeyword
        );
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        return LocalDateTime.parse(value, MOFA_DATE)
            .atZone(ZoneId.of("Asia/Tokyo"))
            .toInstant();
    }

    private Set<String> loadSeenIds() {
        if (!Files.exists(seenFile)) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(Files.readAllLines(seenFile));
        } catch (IOException exception) {
            logger.warning("Failed to load seen MOFA item ids: " + exception.getMessage());
            return new LinkedHashSet<>();
        }
    }

    private void saveSeenIds() {
        try {
            Files.createDirectories(seenFile.getParent());
            List<String> retained = seenIds.stream()
                .skip(Math.max(0, seenIds.size() - config.seenHistoryLimit()))
                .toList();
            Files.write(seenFile, retained);
            seenIds.clear();
            seenIds.addAll(retained);
        } catch (IOException exception) {
            logger.warning("Failed to save seen MOFA item ids: " + exception.getMessage());
        }
    }

    private String text(Element parent, String tagName) {
        return text(parent, tagName, "");
    }

    private String text(Element parent, String tagName, String fallback) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return fallback;
        }
        return nodes.item(0).getTextContent().trim();
    }
}
