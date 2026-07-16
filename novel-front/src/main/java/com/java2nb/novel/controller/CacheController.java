package com.java2nb.novel.controller;


import com.java2nb.novel.core.cache.CacheKey;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.FriendLinkService;
import com.java2nb.novel.service.NewsService;
import io.github.xxyopen.model.resp.RestResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author 11797
 */
@RequestMapping("cache")
@RestController
@Slf4j
@RequiredArgsConstructor
public class CacheController {

    @Value("${cache.manager.password}")
    private String cacheManagerPass;

    private final CacheService cacheService;

    private final BookService bookService;

    private final NewsService newsService;

    private final FriendLinkService friendLinkService;

    /**
     * Làm mới bộ nhớ đệm
     * @param type loại bộ nhớ đệm: 1 đề xuất tác phẩm, 2 tin tức, 3 liên kết bạn bè trang chủ
     * */
    @GetMapping("refresh/{pass}/{type}")
    public RestResult<Void> refreshCache(@PathVariable("type") Byte type, @PathVariable("pass") String pass){
        if(!cacheManagerPass.equals(pass)){
            return RestResult.fail(ResponseStatus.PASSWORD_ERROR);
        }
        switch (type){
            case 1:{
                //Làm mới bộ nhớ đệm đề xuất tác phẩm trang chủ
                cacheService.del(CacheKey.INDEX_BOOK_SETTINGS_KEY);
                bookService.listBookSettingVO();
                break;
            }
            case 2:{
                //Làm mới bộ nhớ đệm tin tức trang chủ
                cacheService.del(CacheKey.INDEX_NEWS_KEY);
                newsService.listIndexNews();
                break;
            }
            case 3:{
                //Làm mới liên kết bạn bè trang chủ
                cacheService.del(CacheKey.INDEX_LINK_KEY);
                friendLinkService.listIndexLink();
                break;
            }
            default:{
                break;
            }

        }

        return RestResult.ok();
    }




}
