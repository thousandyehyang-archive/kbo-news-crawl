/**
 * SlackNotifierImpl 클래스는 Notifier 인터페이스의 구현체로, Slack 웹훅을 사용하여 새로운 뉴스 알림을 전송합니다.
 * - 환경 변수 SLACK_WEBHOOK_URL을 통해 Slack 웹훅 URL을 가져옵니다.
 * - 뉴스 제목, 기사 링크, 및 이미지 URL을 포함하는 JSON 페이로드를 구성합니다.
 * - HttpClient를 사용하여 Slack에 POST 요청을 보내고, 응답을 로깅합니다.
 * 이 클래스는 뉴스 알림 시스템에서 새로운 KBO 뉴스가 도착했을 때, 해당 정보를 Slack 채널로 전달하는 역할을 수행합니다.
 */

package com.example.newscrawler.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

public class SlackNotifierImpl implements Notifier {
    private final HttpClient client;
    private final Logger logger;

    public SlackNotifierImpl(HttpClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    @Override
    public void notify(String title, String articleLink, String imageUrl) {
        try {
            String webhookUrl = System.getenv("SLACK_WEBHOOK_URL");
            if (webhookUrl == null || webhookUrl.isEmpty()) {
                logger.warning("SLACK_WEBHOOK_URL not set.");
                return;
            }
            JSONObject payload = new JSONObject();
            payload.put("text", "✉️ 새 KBO 뉴스가 도착했습니다!");

            JSONArray attachments = new JSONArray();
            JSONObject attachment = new JSONObject();
            attachment.put("title", title);
            attachment.put("title_link", articleLink);
            if (!imageUrl.isEmpty()) {
                attachment.put("image_url", imageUrl);
            }
            attachments.put(attachment);
            payload.put("attachments", attachments);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            logger.info("Slack response: " + response.body());
        } catch (Exception e) {
            logger.warning("Failed to send Slack message: " + e.getMessage());
        }
    }
}
