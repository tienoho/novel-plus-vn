package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.utils.Constants;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.service.BookService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Tác vụ chuyển ảnh mạng đã thu thập sang phương tiện lưu trữ của hệ thống (cục bộ, OSS, FastDFS)
 *
 * @author Administrator
 */
@ConditionalOnProperty(prefix = "pic.save", name = "type", havingValue = "2")
@Service
@RequiredArgsConstructor
@Slf4j
public class CrawlPicTransSchedule {

    private final BookService bookService;
    private final Messages messages;

    @Value("${pic.save.type}")
    private Integer picSaveType;

    @Value("${pic.save.path}")
    private String picSavePath;

    /**
     * Chuyển đổi mỗi 10 phút
     */
    @Scheduled(fixedRate = 1000 * 60 * 10)
    @SneakyThrows
    public void trans() {

        log.info(messages.get("crawl.log.picTransferStarted"));


        List<Book> networkPicBooks = bookService.queryNetworkPicBooks(Constants.LOCAL_PIC_PREFIX,100);
        for (Book book : networkPicBooks) {
            bookService.updateBookPicToLocal(book.getPicUrl(), book.getId());
            //Chuyển một ảnh mỗi 3 giây, tối đa 200 ảnh trong 10 phút
            Thread.sleep(3000);
        }


    }
}
