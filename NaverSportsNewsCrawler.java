import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONObject;

public class NaverSportsNewsCrawler {
    public static void main(String[] args) {
        // 환경변수 KEYWORD가 없으면 기본값 사용 (국내 야구 스포츠)
        String keyword = System.getenv("KEYWORD");
        if (keyword == null || keyword.isEmpty()) {
            keyword = "국내 야구 스포츠";
        }

        // 의존성 주입
        Logger logger = Logger.getLogger(NaverSportsNewsCrawler.class.getName());
        logger.setLevel(Level.INFO);
        HttpClient client = HttpClient.newHttpClient();
        
        NaverApiClient apiClient = new NaverApiClient(client, logger);
        NewsRepository repository = new FileNewsRepository(logger);
        ImageService imageService = new ImageService(apiClient, logger);
        SlackNotifier slackNotifier = new SlackNotifier(client, logger);
        
        // 뉴스 서비스 생성 및 실행
        NewsService newsService = new NewsService(apiClient, repository, imageService, slackNotifier, logger);
        newsService.getAndProcessNews(keyword, 10);
    }
}

// 뉴스 아이템 클래스
class NewsItem implements Comparable<NewsItem> {
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
        // 날짜 기준 내림차순 정렬 (최신순)
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

// 정렬 타입 enum
enum SortType {
    sim("sim"), date("date");

    final String value;
    SortType(String value) {
        this.value = value;
    }
}

// 네이버 API 클라이언트
class NaverApiClient {
    private final HttpClient client;
    private final Logger logger;

    public NaverApiClient(HttpClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    public String getDataFromAPI(String path, String query, int display, int start, SortType sort) throws Exception {
        String url = "https://openapi.naver.com/v1/search/%s".formatted(path);
        String params = String.format("query=%s&display=%d&start=%d&sort=%s",
                URLEncoder.encode(query, "UTF-8"), display, start, sort.value);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .GET()
                .header("X-Naver-Client-Id", System.getenv("NAVER_CLIENT_ID"))
                .header("X-Naver-Client-Secret", System.getenv("NAVER_CLIENT_SECRET"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("API 응답 상태 코드: " + response.statusCode());
        if (response.statusCode() != 200) {
            throw new Exception("API 호출 실패: " + response.body());
        }
        return response.body();
    }
}

// 뉴스 저장소 인터페이스
interface NewsRepository {
    Set<String> getSentArticles();
    void markArticleAsSent(String articleLink);
    void saveNewsToFiles(List<NewsItem> newsItems, Map<String, String> newsImages);
}

// 파일 기반 뉴스 저장소 구현
class FileNewsRepository implements NewsRepository {
    private final Logger logger;
    private final SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public FileNewsRepository(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Set<String> getSentArticles() {
        Set<String> sent = new HashSet<>();
        File file = new File("sent_articles.txt");
        if (!file.exists()) {
            return sent;
        }
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sent.add(line.trim());
            }
        } catch (Exception e) {
            logger.warning("sent_articles.txt 읽기 실패: " + e.getMessage());
        }
        return sent;
    }

    @Override
    public void markArticleAsSent(String articleLink) {
        try (FileWriter fw = new FileWriter("sent_articles.txt", true)) {
            fw.write(articleLink + "\n");
        } catch (Exception e) {
            logger.warning("sent_articles.txt 기록 실패: " + e.getMessage());
        }
    }

    @Override
    public void saveNewsToFiles(List<NewsItem> newsItems, Map<String, String> newsImages) {
        try {
            // CSV와 Markdown 파일 준비
            String csvFileName = "baseball_news.csv";
            File csvFile = new File(csvFileName);
            boolean csvExists = csvFile.exists();

            String mdFileName = "baseball_news.md";
            File mdFile = new File(mdFileName);
            boolean mdExists = mdFile.exists();

            try (FileWriter csvWriter = new FileWriter(csvFile, true);
                 FileWriter mdWriter = new FileWriter(mdFile, true)) {

                if (!csvExists) {
                    csvWriter.write("timestamp,title,image\n");
                }
                if (!mdExists) {
                    mdWriter.write("| Timestamp           | Title                             | Image                                     |\n");
                    mdWriter.write("|---------------------|-----------------------------------|-------------------------------------------|\n");
                }

                for (NewsItem newsItem : newsItems) {
                    String timestamp = outputFormat.format(newsItem.getPubDate());
                    String imageFileName = newsImages.getOrDefault(newsItem.getTitle(), "");

                    // CSV 기록 (이미지 파일명이 없으면 빈 문자열)
                    csvWriter.write(String.format("%s,\"%s\",%s\n", timestamp, newsItem.getTitle(), imageFileName));

                    // Markdown 기록 (이미지 파일이 있으면 ![Image](images/파일명), 없으면 빈 칸)
                    String imageMarkdown = imageFileName.isEmpty() ? "" : String.format("![Image](images/%s)", imageFileName);
                    mdWriter.write(String.format("| %s | %s | %s |\n", timestamp, newsItem.getTitle(), imageMarkdown));
                }
            }

            logger.info("뉴스 데이터가 CSV 및 Markdown 파일에 저장되었습니다.");
        } catch (Exception e) {
            logger.severe("파일 저장 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

// 이미지 서비스 클래스
class ImageService {
    private final NaverApiClient apiClient;
    private final Logger logger;
    private final HttpClient client = HttpClient.newHttpClient();

    public ImageService(NaverApiClient apiClient, Logger logger) {
        this.apiClient = apiClient;
        this.logger = logger;
    }

    public String getImageForNews(String newsTitle) {
        try {
            // 뉴스 제목을 쿼리로 사용하여 이미지 API 호출 (결과 1개)
            String imageResponse = apiClient.getDataFromAPI("image", newsTitle, 1, 1, SortType.sim);
            JSONObject imageJson = new JSONObject(imageResponse);
            JSONArray imageItems = imageJson.getJSONArray("items");
            if (imageItems.length() > 0) {
                JSONObject firstImage = imageItems.getJSONObject(0);
                String imageLink = firstImage.getString("link").split("\\?")[0]; // 쿼리 파라미터 제거

                // 뉴스 제목을 기반으로 안전한 파일명 생성 (특수문자 제거, 길이 제한)
                String sanitizedTitle = newsTitle.replaceAll("[^a-zA-Z0-9가-힣]", "_");
                if (sanitizedTitle.length() > 20) {
                    sanitizedTitle = sanitizedTitle.substring(0, 20);
                }
                String[] parts = imageLink.split("\\.");
                String ext = parts[parts.length - 1];
                String imageFileName = String.format("%d_%s_image.%s", new Date().getTime(), sanitizedTitle, ext);

                // images 폴더 생성 (없으면)
                File imagesDir = new File("images");
                if (!imagesDir.exists()) {
                    imagesDir.mkdir();
                }

                // 이미지 파일을 images 폴더 내에 저장
                String imagePathString = "images/" + imageFileName;
                HttpRequest imageRequest = HttpRequest.newBuilder()
                        .uri(URI.create(imageLink))
                        .build();
                client.send(imageRequest, HttpResponse.BodyHandlers.ofFile(Path.of(imagePathString)));
                logger.info("뉴스 이미지 저장됨: " + imagePathString);
                return imageFileName;
            }
        } catch (Exception e) {
            logger.warning("이미지 검색 실패: " + e.getMessage());
        }
        return "";
    }
}

// Slack 알림 클래스
class SlackNotifier {
    private final HttpClient client;
    private final Logger logger;

    public SlackNotifier(HttpClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    public void sendSlackMessage(String title, String articleLink, String snippet, String imagePublicUrl) {
        try {
            String slackWebhookUrl = System.getenv("SLACK_WEBHOOK_URL");
            if (slackWebhookUrl == null || slackWebhookUrl.isEmpty()) {
                logger.warning("SLACK_WEBHOOK_URL 환경변수가 설정되지 않음.");
                return;
            }
            JSONObject payload = new JSONObject();
            payload.put("text", "✉️ 똑똑, 새 KBO 뉴스가 도착했습니다!");

            JSONArray attachments = new JSONArray();
            JSONObject attachment = new JSONObject();
            // 제목과 링크: 제목 클릭 시 해당 기사로 이동
            attachment.put("title", title);
            attachment.put("title_link", articleLink);
            // 기사 내용 일부(스니펫) 추가
            if (!snippet.isEmpty()) {
                attachment.put("text", snippet);
            }
            // 이미지 URL 추가 (있으면)
            if (!imagePublicUrl.isEmpty()) {
                attachment.put("image_url", imagePublicUrl);
            }
            attachments.put(attachment);
            payload.put("attachments", attachments);

            HttpRequest slackRequest = HttpRequest.newBuilder()
                    .uri(URI.create(slackWebhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> slackResponse = client.send(slackRequest, HttpResponse.BodyHandlers.ofString());
            logger.info("Slack 전송 응답: " + slackResponse.body());
        } catch (Exception e) {
            logger.warning("Slack 전송 실패: " + e.getMessage());
        }
    }
}

// 메인 뉴스 서비스 클래스
class NewsService {
    private final NaverApiClient apiClient;
    private final NewsRepository repository;
    private final ImageService imageService;
    private final SlackNotifier slackNotifier;
    private final Logger logger;
    private final SimpleDateFormat inputFormat;

    public NewsService(NaverApiClient apiClient, NewsRepository repository, 
                       ImageService imageService, SlackNotifier slackNotifier, Logger logger) {
        this.apiClient = apiClient;
        this.repository = repository;
        this.imageService = imageService;
        this.slackNotifier = slackNotifier;
        this.logger = logger;
        this.inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
    }

    // 최신순과 정확도순으로 뉴스를 가져와 중복 제거 후 처리
    public void getAndProcessNews(String keyword, int count) {
        try {
            // 1. 최신순으로 검색한 결과 가져오기
            List<NewsItem> dateResults = getNewsItems(keyword, count, 1, SortType.date);
            
            // 2. 정확도순으로 검색한 결과 가져오기
            List<NewsItem> simResults = getNewsItems(keyword, count, 1, SortType.sim);
            
            // 3. 두 결과를 합치고 중복 제거
            Set<NewsItem> uniqueNewsItems = new LinkedHashSet<>();
            uniqueNewsItems.addAll(dateResults);
            uniqueNewsItems.addAll(simResults);
            
            // 4. 최종 목록을 시간순으로 정렬 (최신순)
            List<NewsItem> finalNewsList = new ArrayList<>(uniqueNewsItems);
            Collections.sort(finalNewsList);
            
            // 5. 이전에 전송한 기사 링크 목록 읽어 중복 전송 방지에 사용
            Set<String> sentArticles = repository.getSentArticles();
            
            // 6. 처리
            processNewsItems(finalNewsList, sentArticles);
            
        } catch (Exception e) {
            logger.severe("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // API에서 뉴스 아이템 리스트를 가져오는 메서드
    private List<NewsItem> getNewsItems(String keyword, int display, int start, SortType sort) throws Exception {
        List<NewsItem> newsItems = new ArrayList<>();
        String newsResponse = apiClient.getDataFromAPI("news.json", keyword, display, start, sort);
        JSONObject newsJson = new JSONObject(newsResponse);
        JSONArray items = newsJson.getJSONArray("items");
        
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String title = item.getString("title").replaceAll("<.*?>", "");
            String articleLink = item.getString("link");
            String snippet = item.optString("description", "");
            snippet = snippet.replaceAll("<b>", "**").replaceAll("</b>", "**");
            
            // 기사 업로드 시간(pubDate) 파싱
            String pubDateStr = item.getString("pubDate");
            Date pubDate = inputFormat.parse(pubDateStr);
            
            newsItems.add(new NewsItem(title, articleLink, snippet, pubDate, pubDateStr));
        }
        
        return newsItems;
    }
    
    // 뉴스 아이템 리스트를 처리하는 메서드
    private void processNewsItems(List<NewsItem> newsItems, Set<String> sentArticles) {
        try {
            // 필터링된 뉴스 아이템과 이미지 매핑 저장
            List<NewsItem> filteredItems = new ArrayList<>();
            Map<String, String> newsImages = new HashMap<>();
            
            for (NewsItem newsItem : newsItems) {
                // 중복 전송 방지를 위해 이미 전송된 기사라면 건너뛰기
                if (sentArticles.contains(newsItem.getLink())) {
                    logger.info("이미 전송된 기사라 건너뜁니다: " + newsItem.getTitle());
                    continue;
                }
                
                // 이미지 검색 및 다운로드
                String imageFileName = imageService.getImageForNews(newsItem.getTitle());
                if (!imageFileName.isEmpty()) {
                    newsImages.put(newsItem.getTitle(), imageFileName);
                }
                
                filteredItems.add(newsItem);
                
                // Slack 메시지 전송
                String imagePublicUrl = "";
                if (!imageFileName.isEmpty()) {
                    String baseUrl = System.getenv("SLACK_IMAGE_BASE_URL");
                    if (baseUrl != null && !baseUrl.isEmpty()) {
                        imagePublicUrl = baseUrl + imageFileName;
                    }
                }
                slackNotifier.sendSlackMessage(newsItem.getTitle(), newsItem.getLink(), 
                                              newsItem.getDescription(), imagePublicUrl);
                
                // 전송한 기사 링크 기록 (중복 전송 방지)
                repository.markArticleAsSent(newsItem.getLink());
                logger.info("뉴스 제목 처리됨 및 Slack 전송: " + newsItem.getTitle());
            }
            
            // 필터링된 기사를 파일에 저장
            repository.saveNewsToFiles(filteredItems, newsImages);
            
        } catch (Exception e) {
            logger.severe("뉴스 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
