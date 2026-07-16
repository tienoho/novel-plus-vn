package com.java2nb.novel.core.crawl;

import com.java2nb.novel.entity.Book;

/**
 * Bộ xử lý truyện được thu thập
 * */
public interface CrawlBookHandler {

    void handle(Book book) throws InterruptedException;

}
