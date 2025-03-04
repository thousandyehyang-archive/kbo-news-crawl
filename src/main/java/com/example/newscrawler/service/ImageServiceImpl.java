package com.example.newscrawler.service;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Date;
import java.util.logging.Logger;

public class ImageServiceImpl implements ImageDownloader {
    private final ApiClient apiClient;
    private final Logger logger;
    private final HttpClient client = HttpClient.newHttpClient();

    public ImageServiceImpl(ApiClient apiClient, Logger logger) {
        this.apiClient = apiClient;
        this.logger = logger;
    }

    @Override
    public String downloadImage(String newsTitle) {
        try {
            // 뉴스 제목을 쿼리로 하여 이미지 API 호출 (결과 1개)
            String imageResponse = apiClient.getData("image", newsTitle, 1, 1, "sim");
            JSONObject imageJson = new JSONObject(imageResponse);
            JSONArray items = imageJson.getJSONArray("items");
            if (items.length() > 0) {
                JSONObject firstImage = items.getJSONObject(0);
                String imageLink = firstImage.getString("link").split("\\?")[0];

                // 뉴스 제목을 기반으로 안전한 파일명 생성
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

                String imagePathString = "images/" + imageFileName;
                HttpRequest imageRequest = HttpRequest.newBuilder()
                        .uri(URI.create(imageLink))
                        .build();
                client.send(imageRequest, HttpResponse.BodyHandlers.ofFile(Path.of(imagePathString)));
                logger.info("Saved image: " + imagePathString);
                return imageFileName;
            }
        } catch (Exception e) {
            logger.warning("Image download failed: " + e.getMessage());
        }
        return "";
    }
}
