package com.java2nb.novel.core.crawl;

import com.java2nb.novel.entity.BookContent;
import com.java2nb.novel.entity.BookIndex;
import lombok.Data;

import java.util.List;

/**
 * Bean đóng gói dữ liệu chương
 * @author Administrator
 */
@Data
public class ChapterBean {

    /**
     * Danh sách chỉ mục chương
     * */
    List<BookIndex> bookIndexList;

    /**
     * Danh sách nội dung chương
     * */
    List<BookContent> bookContentList;
}
