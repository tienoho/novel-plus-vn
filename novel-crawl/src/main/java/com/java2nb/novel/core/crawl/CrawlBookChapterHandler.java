package com.java2nb.novel.core.crawl;

import com.java2nb.novel.entity.Book;

/**
 * Bộ xử lý nội dung chương truyện được thu thập
 * */
public interface CrawlBookChapterHandler {

    void handle(ChapterBean chapterBean);

}
