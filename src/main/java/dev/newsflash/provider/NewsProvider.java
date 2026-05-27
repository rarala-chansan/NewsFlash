package dev.newsflash.provider;

import dev.newsflash.model.NewsItem;
import java.util.List;

public interface NewsProvider {
    String name();

    int initialDelaySeconds();

    int pollIntervalMinutes();

    List<NewsItem> fetchNewItems(boolean allowInitialSuppress) throws Exception;
}
