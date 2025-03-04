package com.example.newscrawler.service;

public interface ApiClient {
    String getData(String path, String query, int display, int start, String sort) throws Exception;
}
