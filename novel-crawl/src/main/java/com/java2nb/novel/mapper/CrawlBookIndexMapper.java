package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.BookIndex;
import org.apache.ibatis.annotations.Param;

/**
 * @author Administrator
 */
public interface CrawlBookIndexMapper extends BookIndexMapper {


    /**
     * Truy vấn chương cuối cùng
     * */
    BookIndex queryLastIndex(@Param("bookId") Long bookId);
}
