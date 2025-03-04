/**
 * NewsItem 클래스는 뉴스 기사의 도메인 모델을 나타냅니다.
 * - 뉴스 기사의 제목(title), 링크(link), 설명(description), 게시일(pubDate) 및 게시일 문자열(pubDateStr)을 보유합니다.
 * - 불변 객체(immutable)로 설계되어 한 번 생성되면 필드 값이 변경되지 않습니다.
 * - Comparable 인터페이스를 구현하여 게시일을 기준으로 내림차순(최신순) 정렬할 수 있도록 합니다.
 * - equals()와 hashCode() 메서드를 오버라이드하여 뉴스 기사의 고유 식별자(link)를 기준으로 객체 동등성을 판단합니다.
 */

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
        // 내림차순(최신순) 정렬: 최신 기사가 먼저 오도록 정렬합니다.
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
