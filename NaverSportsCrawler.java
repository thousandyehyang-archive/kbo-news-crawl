import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NaverSportsCrawler {
    public static void main(String[] args) {
        String url = "https://sports.news.naver.com/kbaseball/news/index.nhn";  
        String outputFile = "baseball_news.csv";

        try (FileWriter writer = new FileWriter(outputFile, true)) {
            Document doc = Jsoup.connect(url).get();
            Elements newsList = doc.select("ul.today_list li a");

            System.out.println("네이버 야구 뉴스 크롤링 시작...");

            for (Element news : newsList) {
                String title = news.text();
                String link = "https://sports.news.naver.com" + news.attr("href");

                // 뉴스 기사 본문 크롤링
                String content = fetchNewsContent(link);

                // 파일 저장
                writer.append(getCurrentTime()).append(",")
                        .append(title).append(",")
                        .append(link).append(",")
                        .append(content.replace(",", " ")).append("\n");

                System.out.println("✔ 크롤링 완료: " + title);
            }
            System.out.println("📄 baseball_news.csv 파일 저장 완료");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String fetchNewsContent(String newsUrl) {
        try {
            Document doc = Jsoup.connect(newsUrl).get();
            return doc.select("div#newsEndContents").text();
        } catch (IOException e) {
            return "기사 본문 가져오기 실패";
        }
    }

    public static String getCurrentTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
