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

    /**
     * processNews 메서드는 지정된 키워드로 각각 10개씩 기사를 검색("date"와 "sim")한 후,
     * 중복 제거 및 최신순 정렬을 거쳐, 아직 전송되지 않은 기사 중 가장 최신의 기사 단 하나만 Slack으로 전송합니다.
     *
     * @param keyword 검색에 사용할 키워드
     * @param count   각 기준당 검색 개수 (여기서는 10)
     */
    public void processNews(String keyword, int count) {
        try {
            // "date"와 "sim" 기준으로 각각 count(10)개씩 기사 검색
            List<NewsItem> dateResults = getNewsItems(keyword, count, 1, "date");
            List<NewsItem> simResults = getNewsItems(keyword, count, 1, "sim");

            // 두 결과를 합치고 중복된 기사를 제거
            Set<NewsItem> uniqueNewsItems = new LinkedHashSet<>();
            uniqueNewsItems.addAll(dateResults);
            uniqueNewsItems.addAll(simResults);

            List<NewsItem> finalNewsList = new ArrayList<>(uniqueNewsItems);
            Collections.sort(finalNewsList); // 최신 기사가 첫 번째에 오도록 정렬

            // 이미 전송한 기사 링크 목록을 불러옴
            Set<String> sentArticles = repository.getSentArticles();

            // 최신 기사 중 아직 보내지 않은 기사 하나를 선택하여 처리
            processNewsItems(finalNewsList, sentArticles);

        } catch (Exception e) {
            logger.severe("Error in processNews: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * getNewsItems 메서드는 네이버 API를 통해 주어진 키워드로 기사를 검색하고,
     * 최근 3일 이내에 발행된 기사만 NewsItem 객체 리스트로 반환합니다.
     *
     * @param keyword 검색어
     * @param display 검색 결과 표시 수
     * @param start   검색 시작 인덱스
     * @param sort    정렬 방식 ("date" 또는 "sim")
     * @return 필터링된 NewsItem 리스트
     * @throws Exception API 호출 또는 JSON 파싱 중 발생한 예외
     */
    private List<NewsItem> getNewsItems(String keyword, int display, int start, String sort) throws Exception {
        List<NewsItem> newsItems = new ArrayList<>();
        String response = apiClient.getData("news.json", keyword, display, start, sort);
        JSONObject newsJson = new JSONObject(response);
        JSONArray items = newsJson.getJSONArray("items");

        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -3); // 최근 3일 이내 기사만 포함

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.getString("title").replaceAll("<.*?>", "");
            
            // 기사 링크 정규화: 쿼리 파라미터 제거
            String rawLink = item.getString("link");
            String articleLink = rawLink.split("\\?")[0];

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

    /**
     * processNewsItems 메서드는 정렬된 뉴스 기사 리스트에서 아직 전송되지 않은 기사 중 가장 최신의 기사 단 하나를 선택하여
     * 이미지 다운로드, Slack 알림 전송, 전송 기록 업데이트 및 파일 저장을 수행합니다.
     *
     * @param newsItems    정렬된 NewsItem 리스트
     * @param sentArticles 이미 전송된 기사 링크의 집합
     */
    private void processNewsItems(List<NewsItem> newsItems, Set<String> sentArticles) {
        try {
            NewsItem articleToSend = null;
            for (NewsItem newsItem : newsItems) {
                if (!sentArticles.contains(newsItem.getLink())) {
                    articleToSend = newsItem;
                    break;
                }
            }
            if (articleToSend == null) {
                logger.info("No new article found to send.");
                return;
            }

            // 이미지 다운로드 처리
            String imageFileName = imageDownloader.downloadImage(articleToSend.getTitle());
            String imagePublicUrl = "";
            String baseUrl = System.getenv("SLACK_IMAGE_BASE_URL");
            if (baseUrl != null && !baseUrl.isEmpty() && !imageFileName.isEmpty()) {
                imagePublicUrl = baseUrl + imageFileName;
            }

            // Slack 알림 전송
            notifier.notify(articleToSend.getTitle(), articleToSend.getLink(), imagePublicUrl);
            repository.markArticleAsSent(articleToSend.getLink());
            sentArticles.add(articleToSend.getLink());
            logger.info("Processed and notified: " + articleToSend.getTitle());

            // 저장할 때 최신 기사 하나만 저장
            List<NewsItem> filteredItems = new ArrayList<>();
            filteredItems.add(articleToSend);
            Map<String, String> newsImages = new HashMap<>();
            if (!imageFileName.isEmpty()) {
                newsImages.put(articleToSend.getTitle(), imageFileName);
            }
            repository.saveNews(filteredItems, newsImages);
        } catch (Exception e) {
            logger.severe("Error processing news items: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
