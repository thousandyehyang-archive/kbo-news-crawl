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
