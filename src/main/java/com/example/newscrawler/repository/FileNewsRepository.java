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
