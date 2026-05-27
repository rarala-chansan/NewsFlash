package dev.newsflash.provider.rss;

import dev.newsflash.config.FilterConfig;
import dev.newsflash.config.RssConfig;
import dev.newsflash.config.RssFeedConfig;
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
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class RssNewsProvider implements NewsProvider {
    private final RssConfig config;
    private final Path seenFile;
    private final Logger logger;
    private final HttpClient client;
    private final Set<String> seenIds;

    public RssNewsProvider(RssConfig config, Path dataFolder, Logger logger) {
        this.config = config;
        this.seenFile = dataFolder.resolve("rss-seen.txt");
        this.logger = logger;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
            .build();
        this.seenIds = loadSeenIds();
    }

    @Override
    public String name() {
        return "RSS";
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
        List<NewsItem> result = new ArrayList<>();
        for (RssFeedConfig feed : config.feeds()) {
            if (!feed.enabled() || feed.url().isBlank()) {
                continue;
            }
            List<NewsItem> items = fetchFeed(feed);
            for (NewsItem item : items) {
                if (seenIds.contains(item.id())) {
                    continue;
                }
                seenIds.add(item.id());
                if (matchesFilter(item, feed.filterConfig())) {
                    result.add(item);
                }
                if (result.size() >= config.maxBroadcastPerPoll()) {
                    break;
                }
            }
            if (result.size() >= config.maxBroadcastPerPoll()) {
                break;
            }
        }

        saveSeenIds();
        logger.info("RSS feed check matched " + result.size() + " item(s).");
        return result;
    }

    private List<NewsItem> fetchFeed(RssFeedConfig feed) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(feed.url()))
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("User-Agent", "NewsFlash Minecraft Plugin")
            .GET()
            .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Unexpected RSS response status for " + feed.name() + ": " + response.statusCode());
        }

        try (InputStream body = response.body()) {
            return parse(feed, body);
        }
    }

    private List<NewsItem> parse(RssFeedConfig feed, InputStream inputStream) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(inputStream);
        Element root = document.getDocumentElement();
        if (root == null) {
            return List.of();
        }

        String rootName = localName(root);
        if (rootName.equalsIgnoreCase("feed")) {
            return parseAtom(feed, document);
        }
        return parseRss(feed, document);
    }

    private List<NewsItem> parseRss(RssFeedConfig feed, Document document) {
        NodeList itemNodes = document.getElementsByTagName("item");
        List<NewsItem> items = new ArrayList<>();
        for (int index = 0; index < itemNodes.getLength(); index++) {
            Element item = (Element) itemNodes.item(index);
            String title = text(item, "title");
            String link = text(item, "link");
            String description = text(item, "description");
            String guid = text(item, "guid");
            String pubDate = text(item, "pubDate");
            String id = stableId(feed, guid, link, title, pubDate);
            items.add(new NewsItem(
                id,
                feed.name(),
                "RSS",
                title,
                stripTags(description),
                link,
                parseDate(pubDate),
                ""
            ));
        }
        return items;
    }

    private List<NewsItem> parseAtom(RssFeedConfig feed, Document document) {
        NodeList entryNodes = document.getElementsByTagNameNS("*", "entry");
        List<NewsItem> items = new ArrayList<>();
        for (int index = 0; index < entryNodes.getLength(); index++) {
            Element entry = (Element) entryNodes.item(index);
            String title = childText(entry, "title");
            String link = atomLink(entry);
            String summary = childText(entry, "summary");
            if (summary.isBlank()) {
                summary = childText(entry, "content");
            }
            String idValue = childText(entry, "id");
            String updated = childText(entry, "updated");
            if (updated.isBlank()) {
                updated = childText(entry, "published");
            }
            String id = stableId(feed, idValue, link, title, updated);
            items.add(new NewsItem(
                id,
                feed.name(),
                "Atom",
                title,
                stripTags(summary),
                link,
                parseDate(updated),
                ""
            ));
        }
        return items;
    }

    private boolean matchesFilter(NewsItem item, FilterConfig filter) {
        if (!filter.enabled()) {
            return true;
        }
        String haystack = (item.title() + "\n" + item.lead() + "\n" + item.type()).toLowerCase();
        for (String keyword : filter.keywords()) {
            if (haystack.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return filter.defaultBroadcast();
    }

    private String stableId(RssFeedConfig feed, String guid, String link, String title, String date) {
        String raw = firstNonBlank(guid, link, title + date);
        return "rss:" + feed.id() + ":" + raw;
    }

    private String atomLink(Element entry) {
        NodeList links = entry.getElementsByTagNameNS("*", "link");
        for (int index = 0; index < links.getLength(); index++) {
            Element link = (Element) links.item(index);
            String rel = link.getAttribute("rel");
            if (rel.isBlank() || rel.equals("alternate")) {
                String href = link.getAttribute("href");
                if (!href.isBlank()) {
                    return href;
                }
            }
        }
        return "";
    }

    private Instant parseDate(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.now();
        }
    }

    private Set<String> loadSeenIds() {
        if (!Files.exists(seenFile)) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(Files.readAllLines(seenFile));
        } catch (IOException exception) {
            logger.warning("Failed to load seen RSS item ids: " + exception.getMessage());
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
            logger.warning("Failed to save seen RSS item ids: " + exception.getMessage());
        }
    }

    private String text(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String childText(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || nodes.item(0).getTextContent() == null) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }

    private String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }

    private String stripTags(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("<[^>]+>", "").trim();
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback == null ? "" : fallback;
    }
}
