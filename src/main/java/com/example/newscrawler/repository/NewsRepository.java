/**
 * NewsRepository 인터페이스는 뉴스 데이터의 저장 및 전송된 기사 링크 관리를 위한 추상화 계층을 제공합니다.
 * - getSentArticles(): 이미 전송된 뉴스 기사의 링크 목록을 반환합니다.
 * - markArticleAsSent(String articleLink): 특정 뉴스 기사의 링크를 전송 완료 목록에 기록합니다.
 * - saveNews(List<NewsItem> newsItems, Map<String, String> newsImages): 수집된 뉴스 기사와 관련 이미지 정보를 파일(CSV, Markdown 등)에 저장합니다.
 */

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
