package com.example.newscrawler;

import com.example.newscrawler.service.*;
import com.example.newscrawler.repository.*;
import java.net.http.HttpClient;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NaverSportsNewsCrawler {
    public static void main(String[] args) {
        // 환경변수 KEYWORD가 없으면 기본값 사용 (예: 국내 야구 스포츠)
        String keyword = System.getenv("KEYWORD");
        if (keyword == null || keyword.isEmpty()) {
            keyword = "국내 야구 스포츠";
        }
        
        Logger logger = Logger.getLogger(NaverSportsNewsCrawler.class.getName());
        logger.setLevel(Level.INFO);
        HttpClient client = HttpClient.newHttpClient();

        // 의존성 주입: 각 인터페이스의 구현체 생성
        ApiClient apiClient = new ApiClientImpl(client, logger);
        NewsRepository repository = new FileNewsRepository(logger);
        ImageDownloader imageDownloader = new ImageServiceImpl(apiClient, logger);
        Notifier notifier = new SlackNotifierImpl(client, logger);

        // NewsService 생성 후 뉴스 처리 실행
        NewsService newsService = new NewsService(apiClient, repository, imageDownloader, notifier, logger);
        newsService.processNews(keyword, 1);
    }
}
