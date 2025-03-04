package com.example.newscrawler.model;

import java.util.Date;
import java.util.Objects;

public class NewsItem implements Comparable<NewsItem> {
    private final String title;
    private final String link;
    private final String description;
    private final Date pubDate;
    private final String pubDateStr;

    public NewsItem(String title, String link, String description, Date pubDate, String pubDateStr) {
        this.title = title;
        this.link = link;
        this.description = description;
        this.pubDate = pubDate;
        this.pubDateStr = pubDateStr;
    }

    public String getTitle() {
        return title;
    }
    public String getLink() {
        return link;
    }
    public String getDescription() {
        return description;
    }
    public Date getPubDate() {
        return pubDate;
    }
    public String getPubDateStr() {
        return pubDateStr;
    }

    @Override
    public int compareTo(NewsItem other) {
        // 내림차순(최신순) 정렬
        return other.pubDate.compareTo(this.pubDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewsItem newsItem = (NewsItem) o;
        return Objects.equals(link, newsItem.link);
    }

    @Override
    public int hashCode() {
        return Objects.hash(link);
    }
}
