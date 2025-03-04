/**
 * FileNewsRepository 클래스는 NewsRepository 인터페이스의 파일 기반 구현체입니다.
 * - getSentArticles(): 파일(sent_articles.txt)에서 이미 전송된 뉴스 기사 링크 목록을 읽어 반환합니다.
 * - markArticleAsSent(String articleLink): 파일(sent_articles.txt)에 전송된 뉴스 기사 링크를 추가 기록합니다.
 * - saveNews(List<NewsItem> newsItems, Map<String, String> newsImages): 수집된 뉴스 기사와 해당 이미지 정보를
 *   CSV 파일(baseball_news.csv)과 Markdown 파일(baseball_news.md)로 저장합니다.
 * - CSV 파일은 "timestamp,title,image" 형식으로 기록되며, 파일이 없을 경우 헤더를 작성합니다.
 * - Markdown 파일은 표 형식으로 작성되며, 첫 실행 시 헤더 행을 추가합니다.
 * 날짜 포맷은 "yyyy-MM-dd HH:mm:ss"로 지정되어 있으며, 예외 발생 시 Logger를 통해 경고 또는 에러 메시지를 출력합니다.
 */

package com.example.newscrawler.repository;

import com.example.newscrawler.model.NewsItem;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class FileNewsRepository implements NewsRepository {
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
            logger.warning("Failed to read sent_articles.txt: " + e.getMessage());
        }
        return sent;
    }

    @Override
    public void markArticleAsSent(String articleLink) {
        try (FileWriter fw = new FileWriter("sent_articles.txt", true)) {
            fw.write(articleLink + "\n");
        } catch (Exception e) {
            logger.warning("Failed to write to sent_articles.txt: " + e.getMessage());
        }
    }

    @Override
    public void saveNews(List<NewsItem> newsItems, Map<String, String> newsImages) {
        try {
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

                    csvWriter.write(String.format("%s,\"%s\",%s\n", timestamp, newsItem.getTitle(), imageFileName));

                    String imageMarkdown = imageFileName.isEmpty() ? "" : String.format("![Image](images/%s)", imageFileName);
                    mdWriter.write(String.format("| %s | %s | %s |\n", timestamp, newsItem.getTitle(), imageMarkdown));
                }
            }
            logger.info("News saved to CSV and Markdown files.");
        } catch (Exception e) {
            logger.severe("Error saving files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
