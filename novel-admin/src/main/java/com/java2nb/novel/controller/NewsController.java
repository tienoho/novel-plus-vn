package com.java2nb.novel.controller;

import com.java2nb.common.config.CacheKey;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.domain.NewsDO;
import com.java2nb.novel.service.NewsService;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Bảng tin tức
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 10:05:51
 */

@Controller
@RequestMapping("/novel/news")
public class NewsController {

    @Autowired
    private NewsService newsService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping()
    @RequiresPermissions("novel:news:news")
    String News() {
        return "novel/news/news";
    }

    @ApiOperation(value = "Lấy danh sách bảng tin tức", notes = "Lấy danh sách bảng tin tức")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:news:news")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<NewsDO> newsList = newsService.list(query);
        int total = newsService.count(query);
        PageBean pageBean = new PageBean(newsList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng tin tức", notes = "Trang thêm bảng tin tức")
    @GetMapping("/add")
    @RequiresPermissions("novel:news:add")
    String add() {
        return "novel/news/add";
    }

    @ApiOperation(value = "Trang sửa bảng tin tức", notes = "Trang sửa bảng tin tức")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:news:edit")
    String edit(@PathVariable("id") Long id, Model model) {
        NewsDO news = newsService.get(id);
        model.addAttribute("news", news);
        return "novel/news/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng tin tức", notes = "Trang chi tiết bảng tin tức")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:news:detail")
    String detail(@PathVariable("id") Long id, Model model) {
        NewsDO news = newsService.get(id);
        model.addAttribute("news", news);
        return "novel/news/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng tin tức", notes = "Thêm bảng tin tức")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:news:add")
    public R save(NewsDO news) {
        if (newsService.save(news) > 0) {
            redisTemplate.delete(CacheKey.INDEX_NEWS_KEY);
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng tin tức", notes = "Sửa bảng tin tức")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:news:edit")
    public R update(NewsDO news) {
        newsService.update(news);
        redisTemplate.delete(CacheKey.INDEX_NEWS_KEY);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng tin tức", notes = "Xóa bảng tin tức")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:news:remove")
    public R remove(Long id) {
        if (newsService.remove(id) > 0) {
            redisTemplate.delete(CacheKey.INDEX_NEWS_KEY);
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng tin tức", notes = "Xóa hàng loạt bảng tin tức")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:news:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
        newsService.batchRemove(ids);
        redisTemplate.delete(CacheKey.INDEX_NEWS_KEY);
        return R.ok();
    }

}
