package com.java2nb.novel.service;


import io.github.xxyopen.model.page.PageBean;
import com.java2nb.novel.entity.News;
import com.java2nb.novel.vo.NewsVO;

import java.util.List;

/**
 * @author 11797
 */
public interface NewsService {

    /**
     * Truy vấn tin tức trang chủ
     * @return
     * */
    List<News> listIndexNews();

    /**
     * Truy vấn tin tức
     * @param newsId ID tin tức
     * @return tin tức
     * */
    News queryNewsInfo(Long newsId);

    /**
     * Truy vấn phân trang danh sách tin tức
     * @param page số trang
     * @param pageSize kích thước trang
     * @return dữ liệu phân trang tin tức
     * */
    PageBean<News> listByPage(int page, int pageSize);

    /**
     * Tăng lượt đọc tin tức
     * @param newsId ID tin tức
     * */
    void addReadCount(Integer newsId);
}
