package dev.newsflash.model;

import java.time.Instant;

public record NewsItem(
    String id,
    String source,
    String type,
    String title,
    String lead,
    String url,
    Instant publishedAt,
    String matchedKeyword
) {
}
