package com.java2nb.novel.controller;

import com.java2nb.novel.core.cache.CacheKey;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.utils.HttpUtil;
import io.github.xxyopen.model.page.PageBean;

import com.java2nb.novel.entity.CrawlSingleTask;
import com.java2nb.novel.entity.CrawlSource;
import com.java2nb.novel.service.CrawlService;
import io.github.xxyopen.model.resp.RestResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Administrator
 */
@RestController
@RequestMapping("crawl")
@RequiredArgsConstructor
public class CrawlController {

    private final CrawlService crawlService;

    private final CacheService cacheService;
    /**
     * Thêm nguồn thu thập
     * */
    @PostMapping("addCrawlSource")
    public RestResult<Void> addCrawlSource(CrawlSource source){
        crawlService.addCrawlSource(source);

        return RestResult.ok();

    }

    /**
     * Truy vấn phân trang nguồn thu thập
     * */
    @GetMapping("listCrawlByPage")
    public RestResult<PageBean<CrawlSource>> listCrawlByPage(@RequestParam(value = "curr", defaultValue = "1") int page, @RequestParam(value = "limit", defaultValue = "10") int pageSize){

        return RestResult.ok(crawlService.listCrawlByPage(page,pageSize));
    }
    /**
     * Lấy nguồn thu thập
     * */
    @GetMapping("getCrawlSource/{id}")
    public RestResult<CrawlSource> getCrawlSource(@PathVariable("id") Integer id){
        CrawlSource crawlSource=  crawlService.getCrawlSource(id);
        return RestResult.ok(crawlSource);

    }

    /**
     * Kiểm thử quy tắc
     * @param rule
     * @param url
     * @param isRefresh
     * @return
     */
    @PostMapping("testParse")
    public RestResult<Object> testParse(String rule,String url,String isRefresh){

        Map<String,Object> resultMap=new HashMap<>();
        String html =null;
        if(url.startsWith("https://")||url.startsWith("http://")){
            String refreshCache="1";
            if(!refreshCache.equals(isRefresh)) {
                html = cacheService.get(CacheKey.BOOK_TEST_PARSE + url);
                if (html == null) {
                    isRefresh="1";
                }
            }
            if(refreshCache.equals(isRefresh)){
                html = HttpUtil.getByHttpClientWithChrome(url);
                if (html != null) {
                    cacheService.set(CacheKey.BOOK_TEST_PARSE + url, html, 60 * 10);
                }else{
                    resultMap.put("msg","html is null");
                    return RestResult.ok(resultMap);
                }
            }
        }else{
            resultMap.put("html","url is null");
            return RestResult.ok(resultMap);
        }
        Pattern pattern = Pattern.compile(rule);
        Matcher matcher = pattern.matcher(html);
        boolean isFind = matcher.find();
        resultMap.put("matched", isFind);
        if(isFind){
            resultMap.put("matchResult", matcher.group(1));
        }
       // resultMap.put("url",url);
        return RestResult.ok(resultMap);
    }
    /**
     * Cập nhật nguồn thu thập
     * */
    @PostMapping("updateCrawlSource")
    public RestResult<Void> updateCrawlSource(CrawlSource source) {
        crawlService.updateCrawlSource(source);
        return RestResult.ok();

    }
    /**
     * Bật hoặc dừng trình thu thập
     * */
    @PostMapping("openOrCloseCrawl")
    public RestResult<Void> openOrCloseCrawl(Integer sourceId,Byte sourceStatus){

        crawlService.openOrCloseCrawl(sourceId,sourceStatus);

        return RestResult.ok();
    }

    /**
     * Thêm tác vụ thu thập từng truyện
     * */
    @PostMapping("addCrawlSingleTask")
    public RestResult<Void> addCrawlSingleTask(CrawlSingleTask singleTask){
        crawlService.addCrawlSingleTask(singleTask);

        return RestResult.ok();

    }

    /**
     * Truy vấn phân trang tác vụ thu thập từng truyện
     * */
    @GetMapping("listCrawlSingleTaskByPage")
    public RestResult<PageBean<CrawlSingleTask>> listCrawlSingleTaskByPage(@RequestParam(value = "curr", defaultValue = "1") int page, @RequestParam(value = "limit", defaultValue = "10") int pageSize){

        return RestResult.ok(crawlService.listCrawlSingleTaskByPage(page,pageSize));
    }

    /**
     * Xóa tác vụ thu thập
     * */
    @DeleteMapping("delCrawlSingleTask/{id}")
    public RestResult<Void> delCrawlSingleTask(@PathVariable("id") Long id){

        crawlService.delCrawlSingleTask(id);

        return RestResult.ok();
    }

    /**
     * Truy vấn tiến độ tác vụ thu thập
     * */
    @GetMapping("getTaskProgress/{id}")
    public RestResult<Integer> getTaskProgress(@PathVariable("id") Long id){
        return RestResult.ok(crawlService.getTaskProgress(id));
    }




}
