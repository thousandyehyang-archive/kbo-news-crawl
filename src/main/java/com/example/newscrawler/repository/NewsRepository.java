package com.example.newscrawler.repository;

import com.example.newscrawler.model.NewsItem;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface NewsRepository {
    Set<String> getSentArticles();
    void markArticleAsSent(String articleLink);
    void saveNews(List<NewsItem> newsItems, Map<String, String> newsImages);
}
