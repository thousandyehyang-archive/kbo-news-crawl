/**
 * NewsService 클래스는 뉴스 데이터를 검색, 처리 및 저장하는 주요 비즈니스 로직을 수행합니다.
 * - 네이버 API를 통해 최신 뉴스 기사를 검색하고, "date" 및 "sim" 정렬 방식으로 결과를 수집합니다.
 * - 최근 3일 이내에 발행된 기사만 필터링하여 NewsItem 객체 리스트로 변환합니다.
 * - 중복 기사를 제거한 후, 이미 전송된 기사는 건너뛰고, 새 기사의 경우 이미지 다운로드, Slack 알림 전송, 
 *   전송 기록 업데이트 및 파일(CSV, Markdown) 저장 작업을 수행합니다.
 * - ApiClient: 외부 API 호출을 통한 뉴스 데이터 수집 기능 제공
 * - NewsRepository: 뉴스 기사 저장 및 전송 기록 관리 기능 제공
 * - ImageDownloader: 뉴스 관련 이미지 다운로드 기능 제공
 * - Notifier: Slack 등의 알림 전송 기능 제공
 */

package com.example.newscrawler.service;

import com.example.newscrawler.model.NewsItem;
import com.example.newscrawler.repository.NewsRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class NewsService {
    private final ApiClient apiClient;
    private final NewsRepository repository;
    private final ImageDownloader imageDownloader;
    private final Notifier notifier;
    private final Logger logger;
    private final SimpleDateFormat inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);

    public NewsService(ApiClient apiClient, NewsRepository repository,
                       ImageDownloader imageDownloader, Notifier notifier, Logger logger) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.imageDownloader = imageDownloader;
        this.notifier = notifier;
        this.logger = logger;
    }

    public void processNews(String keyword, int count) {
        try {
            // 최신순 및 정확도순 검색 결과 가져오기
            List<NewsItem> dateResults = getNewsItems(keyword, count, 1, "date");
            List<NewsItem> simResults = getNewsItems(keyword, count, 1, "sim");

            // 두 결과를 합치고 중복 제거
            Set<NewsItem> uniqueNewsItems = new LinkedHashSet<>();
            uniqueNewsItems.addAll(dateResults);
            uniqueNewsItems.addAll(simResults);

            List<NewsItem> finalNewsList = new ArrayList<>(uniqueNewsItems);
            Collections.sort(finalNewsList);

            Set<String> sentArticles = repository.getSentArticles();

            processNewsItems(finalNewsList, sentArticles);

        } catch (Exception e) {
            logger.severe("Error in processNews: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private List<NewsItem> getNewsItems(String keyword, int display, int start, String sort) throws Exception {
        List<NewsItem> newsItems = new ArrayList<>();
        String response = apiClient.getData("news.json", keyword, display, start, sort);
        JSONObject newsJson = new JSONObject(response);
        JSONArray items = newsJson.getJSONArray("items");

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -3); // 최근 3일 이내 기사만

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.getString("title").replaceAll("<.*?>", "");
            String articleLink = item.getString("link");
            String snippet = item.optString("description", "");
            snippet = snippet.replaceAll("<b>", "**").replaceAll("</b>", "**");

            String pubDateStr = item.getString("pubDate");
            Date pubDate = sdf.parse(pubDateStr);
            if (pubDate.before(calendar.getTime())) {
                continue;
            }

            newsItems.add(new NewsItem(title, articleLink, snippet, pubDate, pubDateStr));
        }
        return newsItems;
    }

    private void processNewsItems(List<NewsItem> newsItems, Set<String> sentArticles) {
        try {
            List<NewsItem> filteredItems = new ArrayList<>();
            Map<String, String> newsImages = new HashMap<>();

            for (NewsItem newsItem : newsItems) {
                if (sentArticles.contains(newsItem.getLink())) {
                    logger.info("Article already sent: " + newsItem.getTitle());
                    continue;
                }

                String imageFileName = imageDownloader.downloadImage(newsItem.getTitle());
                if (!imageFileName.isEmpty()) {
                    newsImages.put(newsItem.getTitle(), imageFileName);
                }

                filteredItems.add(newsItem);

                String imagePublicUrl = "";
                String baseUrl = System.getenv("SLACK_IMAGE_BASE_URL");
                if (baseUrl != null && !baseUrl.isEmpty() && !imageFileName.isEmpty()) {
                    imagePublicUrl = baseUrl + imageFileName;
                }

                notifier.notify(newsItem.getTitle(), newsItem.getLink(), imagePublicUrl);
                repository.markArticleAsSent(newsItem.getLink());
                logger.info("Processed and notified: " + newsItem.getTitle());
            }

            repository.saveNews(filteredItems, newsImages);
        } catch (Exception e) {
            logger.severe("Error processing news items: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
