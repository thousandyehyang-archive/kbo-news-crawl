/**
 * NaverSportsNewsCrawler는 네이버 API를 통해 뉴스 데이터를 수집, 처리 및 알림 전송을 수행하는 애플리케이션의 진입점입니다.
 * - 환경 변수 KEYWORD를 통해 검색어를 받아, 지정된 검색어로 뉴스를 검색합니다.
 * - HttpClient와 Logger를 초기화하여 API 호출 및 로깅을 처리합니다.
 * - ApiClient, NewsRepository, ImageDownloader, Notifier 인터페이스의 구체 구현체를 생성하여
 *   의존성 주입을 통해 뉴스 수집, 파일 저장, 이미지 다운로드 및 Slack 알림 전송 기능을 구성합니다.
 * - NewsService를 통해 API에서 뉴스 데이터를 가져오고, 중복 기사 제거, 이미지 저장, 알림 전송 및 최종 데이터 저장을 수행합니다.
 */

package com.example.newscrawler;

import com.example.newscrawler.service.*;
import com.example.newscrawler.repository.*;
import java.net.http.HttpClient;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NaverSportsNewsCrawler {
    public static void main(String[] args) {
        // 환경변수 KEYWORD가 없으면 기본값 사용
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
