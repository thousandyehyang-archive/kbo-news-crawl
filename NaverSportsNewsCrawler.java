import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
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
        Monitoring monitoring = new Monitoring();
        // 예시: 최신 1개 기사를 가져옴
        monitoring.getNews(keyword, 1, 1, SortType.date);
    }
}

enum SortType {
    sim("sim"), date("date");

    final String value;
    SortType(String value) {
        this.value = value;
    }
}

class Monitoring {
    private final Logger logger;
    private final HttpClient client;

    public Monitoring() {
        logger = Logger.getLogger(Monitoring.class.getName());
        logger.setLevel(Level.INFO);
        client = HttpClient.newHttpClient();
    }

    // 뉴스와 이미지 데이터를 API를 통해 가져와 CSV, Markdown 파일에 저장하고 Slack으로 전송
    public void getNews(String keyword, int display, int start, SortType sort) {
        try {
            // 1. 뉴스 데이터 조회
            String newsResponse = getDataFromAPI("news.json", keyword, display, start, sort);
            JSONObject newsJson = new JSONObject(newsResponse);
            JSONArray items = newsJson.getJSONArray("items");

            // 이전에 전송한 기사 링크 목록 읽어 중복 전송 방지에 사용
            Set<String> sentArticles = readSentArticles();

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

                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    String title = item.getString("title").replaceAll("<.*?>", "");
                    // 기사 링크 추출 (네이버 뉴스 URL 또는 원문 URL)
                    String articleLink = item.getString("link");
                    // 기사 내용 일부(스니펫)는 description 필드를 사용하고, <b> 태그는 Markdown 형식의 ** 로 변환
                    String snippet = item.optString("description", "");
                    snippet = snippet.replaceAll("<b>", "**").replaceAll("</b>", "**");

                    // 중복 전송 방지를 위해 이미 전송된 기사라면 건너뛰기
                    if (sentArticles.contains(articleLink)) {
                        logger.info("이미 전송된 기사라 건너뜁니다: " + title);
                        continue;
                    }

                    // 기사 업로드 시간(pubDate) 사용 (예: "Mon, 26 Sep 2016 07:50:00 +0900")
                    String pubDate = item.getString("pubDate");
                    SimpleDateFormat inputFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH);
                    SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String timestamp = outputFormat.format(inputFormat.parse(pubDate));

                    // 각 뉴스 항목마다 해당 뉴스 제목을 기반으로 이미지 검색 및 다운로드
                    String imageFileName = getImageForNews(title);

                    // CSV 기록 (이미지 파일명이 없으면 빈 문자열)
                    csvWriter.write(String.format("%s,\"%s\",%s\n", timestamp, title, imageFileName));

                    // Markdown 기록 (이미지 파일이 있으면 ![Image](images/파일명), 없으면 빈 칸)
                    String imageMarkdown = imageFileName.isEmpty() ? "" : String.format("![Image](images/%s)", imageFileName);
                    mdWriter.write(String.format("| %s | %s | %s |\n", timestamp, title, imageMarkdown));

                    // Slack 메시지 전송: 기사 제목, 링크, 스니펫, 이미지 URL 포함
                    String imagePublicUrl = "";
                    if (!imageFileName.isEmpty()) {
                        String baseUrl = System.getenv("SLACK_IMAGE_BASE_URL");
                        if (baseUrl != null && !baseUrl.isEmpty()) {
                            imagePublicUrl = baseUrl + imageFileName;
                        }
                    }
                    sendSlackMessage(title, articleLink, snippet, imagePublicUrl);

                    // 전송한 기사 링크 기록 (중복 전송 방지)
                    markArticleAsSent(articleLink);
                    logger.info("뉴스 제목 처리됨 및 Slack 전송: " + title);
                }
            }

            logger.info("뉴스 데이터가 CSV 및 Markdown 파일에 저장되었습니다.");
        } catch (Exception e) {
            logger.severe("오류 발생: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 각 뉴스 제목에 대해 관련 이미지를 검색하고 다운로드하는 메서드
    private String getImageForNews(String newsTitle) {
        try {
            // 뉴스 제목을 쿼리로 사용하여 이미지 API 호출 (결과 1개)
            String imageResponse = getDataFromAPI("image", newsTitle, 1, 1, SortType.sim);
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

    // Slack 메시지 전송: 기사 제목, 링크, 스니펫, 이미지 URL 포함
    private void sendSlackMessage(String title, String articleLink, String snippet, String imagePublicUrl) {
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

    // 네이버 API 호출 메서드
    private String getDataFromAPI(String path, String query, int display, int start, SortType sort) throws Exception {
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

    // 이미 전송된 기사 목록 파일(sent_articles.txt)에서 기사 링크를 읽어 Set으로 반환
    private Set<String> readSentArticles() {
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

    // 전송된 기사 링크를 sent_articles.txt 파일에 추가하여 저장
    private void markArticleAsSent(String articleLink) {
        try (FileWriter fw = new FileWriter("sent_articles.txt", true)) {
            fw.write(articleLink + "\n");
        } catch (Exception e) {
            logger.warning("sent_articles.txt 기록 실패: " + e.getMessage());
        }
    }
}
