package com.java2nb.novel.controller;

import com.github.pagehelper.PageInfo;
import io.github.xxyopen.model.page.PageBean;
import com.java2nb.novel.entity.News;
import com.java2nb.novel.service.NewsService;
import io.github.xxyopen.model.resp.RestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 11797
 */
@RequestMapping("news")
@RestController
@Slf4j
@RequiredArgsConstructor
public class NewsController {

    private final NewsService newsService;

    /**
     * Truy vấn tin tức trang chủ
     * */
    @GetMapping("listIndexNews")
    public RestResult<List<News>> listIndexNews(){
        return RestResult.ok(newsService.listIndexNews());
    }

    /**
     * Truy vấn phân trang danh sách tin tức
     * */
    @GetMapping("listByPage")
    public RestResult<PageBean<News>> listByPage(@RequestParam(value = "curr", defaultValue = "1") int page, @RequestParam(value = "limit", defaultValue = "5") int pageSize){
        return RestResult.ok(newsService.listByPage(page,pageSize));
    }

    /**
     * Tăng lượt đọc tin tức
     * */
    @PostMapping("addReadCount")
    public RestResult<Void> addReadCount(@RequestParam(value = "newsId") Integer newsId){
        newsService.addReadCount(newsId);
        return RestResult.ok();
    }



}
