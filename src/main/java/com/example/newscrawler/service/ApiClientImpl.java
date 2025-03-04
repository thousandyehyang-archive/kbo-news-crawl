package com.example.newscrawler.service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Logger;

public class ApiClientImpl implements ApiClient {
    private final HttpClient client;
    private final Logger logger;

    public ApiClientImpl(HttpClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    @Override
    public String getData(String path, String query, int display, int start, String sort) throws Exception {
        String url = "https://openapi.naver.com/v1/search/%s".formatted(path);
        String params = String.format("query=%s&display=%d&start=%d&sort=%s",
                URLEncoder.encode(query, "UTF-8"), display, start, sort);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url + "?" + params))
                .GET()
                .header("X-Naver-Client-Id", System.getenv("NAVER_CLIENT_ID"))
                .header("X-Naver-Client-Secret", System.getenv("NAVER_CLIENT_SECRET"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        logger.info("API Response Code: " + response.statusCode());
        if (response.statusCode() != 200) {
            throw new Exception("API call failed: " + response.body());
        }
        return response.body();
    }
}
