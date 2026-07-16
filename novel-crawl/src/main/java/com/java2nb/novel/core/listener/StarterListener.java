package com.java2nb.novel.core.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.crawl.CrawlParser;
import com.java2nb.novel.core.crawl.RuleBean;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.entity.CrawlSingleTask;
import com.java2nb.novel.entity.CrawlSource;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.CrawlService;
import com.java2nb.novel.utils.Constants;
import jakarta.servlet.ServletContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Administrator
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class StarterListener implements ServletContextInitializer {

    private final BookService bookService;

    private final CrawlService crawlService;

    private final CrawlParser crawlParser;

    private final Messages messages;

    @Value("${crawl.update.thread}")
    private int updateThreadCount;

    @Override
    public void onStartup(ServletContext servletContext) {
        for (int i = 0; i < updateThreadCount; i++) {
            new Thread(() -> {
                log.info(messages.get("crawl.log.autoUpdateStarted"));
                while (true) {
                    try {
                        //1. Truy vấn 100 truyện đầu tiên cần cập nhật, có thời gian cập nhật chương mới nhất trong vòng một tháng
                        Date currentDate = new Date();
                        Date startDate = DateUtils.addDays(currentDate, -30);
                        List<Book> bookList;
                        synchronized (this) {
                            bookList = bookService.queryNeedUpdateBook(startDate, 100);
                        }
                        for (Book needUpdateBook : bookList) {
                            try {
                                //Truy vấn quy tắc nguồn thu thập
                                CrawlSource source = crawlService.queryCrawlSource(needUpdateBook.getCrawlSourceId());
                                RuleBean ruleBean = new ObjectMapper().readValue(source.getCrawlRule(), RuleBean.class);
                                //Phân tích thông tin cơ bản của truyện
                                crawlParser.parseBook(ruleBean, needUpdateBook.getCrawlBookId(), book -> {
                                    //Chỉ cập nhật truyện đã tồn tại
                                    book.setId(needUpdateBook.getId());
                                    book.setWordCount(needUpdateBook.getWordCount());
                                    if (needUpdateBook.getPicUrl() != null && needUpdateBook.getPicUrl()
                                        .contains(Constants.LOCAL_PIC_PREFIX)) {
                                        //Không cập nhật ảnh được lưu cục bộ
                                        book.setPicUrl(null);
                                    }
                                    //Truy vấn các chương đã tồn tại
                                    Map<Integer, BookIndex> existBookIndexMap = bookService.queryExistBookIndexMap(
                                        needUpdateBook.getId());
                                    //Phân tích mục lục chương
                                    crawlParser.parseBookIndexAndContent(needUpdateBook.getCrawlBookId(), book,
                                        ruleBean, needUpdateBook.getCrawlSourceId(), existBookIndexMap,
                                        chapter -> bookService.updateBookAndIndexAndContent(book,
                                            chapter.getBookIndexList(),
                                            chapter.getBookContentList(), existBookIndexMap), null);
                                });
                            } catch (Exception e) {
                                log.error(e.getMessage(), e);
                            }

                        }
                        //  Tạm dừng 10 phút
                        TimeUnit.MINUTES.sleep(10);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }

                }
            }).start();


        }

        new Thread(() -> {
            log.info(messages.get("crawl.log.singleTaskStarted"));
            while (true) {
                CrawlSingleTask task = null;
                byte crawlStatus = 0;
                try {
                    //Lấy tác vụ thu thập
                    task = crawlService.getCrawlSingleTask();

                    if (task != null) {
                        //Truy vấn quy tắc thu thập
                        CrawlSource source = crawlService.queryCrawlSource(task.getSourceId());
                        RuleBean ruleBean = new ObjectMapper().readValue(source.getCrawlRule(), RuleBean.class);
                        if (crawlService.parseBookAndSave(task.getCatId(), ruleBean, task.getSourceId(),
                            task.getSourceBookId(), task)) {
                            //Thu thập thành công
                            crawlStatus = 1;
                        }

                    }

                    //Tạm dừng 1 phút
                    TimeUnit.MINUTES.sleep(1);

                } catch (Exception e) {
                    log.error(e.getMessage(), e);
                }

                if (task != null) {
                    crawlService.updateCrawlSingleTask(task, crawlStatus);
                }

            }
        }).start();
    }
}
