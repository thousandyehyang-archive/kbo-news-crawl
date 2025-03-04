package com.example.newscrawler.service;

public interface Notifier {
    void notify(String title, String articleLink, String imageUrl);
}
