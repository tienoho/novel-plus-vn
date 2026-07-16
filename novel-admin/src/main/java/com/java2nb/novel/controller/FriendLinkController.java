package com.java2nb.novel.controller;

import com.java2nb.common.config.CacheKey;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.domain.FriendLinkDO;
import com.java2nb.novel.service.FriendLinkService;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 15:12:25
 */

@Controller
@RequestMapping("/novel/friendLink")
public class FriendLinkController {

    @Autowired
    private FriendLinkService friendLinkService;
    @Autowired
    private StringRedisTemplate redisTemplate;

    @GetMapping()
    @RequiresPermissions("novel:friendLink:friendLink")
    String FriendLink() {
        return "novel/friendLink/friendLink";
    }

    @ApiOperation(value = "Lấy danh sách", notes = "Lấy danh sách")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:friendLink:friendLink")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<FriendLinkDO> friendLinkList = friendLinkService.list(query);
        int total = friendLinkService.count(query);
        PageBean pageBean = new PageBean(friendLinkList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm mới", notes = "Trang thêm mới")
    @GetMapping("/add")
    @RequiresPermissions("novel:friendLink:add")
    String add() {
        return "novel/friendLink/add";
    }

    @ApiOperation(value = "Trang chỉnh sửa", notes = "Trang chỉnh sửa")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:friendLink:edit")
    String edit(@PathVariable("id") Integer id, Model model) {
        FriendLinkDO friendLink = friendLinkService.get(id);
        model.addAttribute("friendLink", friendLink);
        return "novel/friendLink/edit";
    }

    @ApiOperation(value = "Trang chi tiết", notes = "Trang chi tiết")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:friendLink:detail")
    String detail(@PathVariable("id") Integer id, Model model) {
        FriendLinkDO friendLink = friendLinkService.get(id);
        model.addAttribute("friendLink", friendLink);
        return "novel/friendLink/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm mới", notes = "Thêm mới")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:friendLink:add")
    public R save(@Validated FriendLinkDO friendLink) {
        if (friendLinkService.save(friendLink) > 0) {
            redisTemplate.delete(CacheKey.INDEX_LINK_KEY);
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Chỉnh sửa", notes = "Chỉnh sửa")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:friendLink:edit")
    public R update(@Validated FriendLinkDO friendLink) {
        friendLinkService.update(friendLink);
        redisTemplate.delete(CacheKey.INDEX_LINK_KEY);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa", notes = "Xóa")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:friendLink:remove")
    public R remove(Integer id) {
        if (friendLinkService.remove(id) > 0) {
            redisTemplate.delete(CacheKey.INDEX_LINK_KEY);
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt", notes = "Xóa hàng loạt")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:friendLink:batchRemove")
    public R remove(@RequestParam("ids[]") Integer[] ids) {
        friendLinkService.batchRemove(ids);
        redisTemplate.delete(CacheKey.INDEX_LINK_KEY);
        return R.ok();
    }

}
