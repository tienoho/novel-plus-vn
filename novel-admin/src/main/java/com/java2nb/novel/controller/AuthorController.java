package com.java2nb.novel.controller;

import java.util.List;
import java.util.Map;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import io.swagger.annotations.ApiOperation;


import com.java2nb.novel.domain.AuthorDO;
import com.java2nb.novel.service.AuthorService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng tác giả
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-05-13 11:16:51
 */

@Controller
@RequestMapping("/novel/author")
public class AuthorController {
    @Autowired
    private AuthorService authorService;

    @GetMapping()
    @RequiresPermissions("novel:author:author")
    String Author() {
        return "novel/author/author";
    }

    @ApiOperation(value = "Lấy danh sách bảng tác giả", notes = "Lấy danh sách bảng tác giả")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:author:author")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<AuthorDO> authorList = authorService.list(query);
        int total = authorService.count(query);
        PageBean pageBean = new PageBean(authorList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng tác giả", notes = "Trang thêm bảng tác giả")
    @GetMapping("/add")
    @RequiresPermissions("novel:author:add")
    String add() {
        return "novel/author/add";
    }

    @ApiOperation(value = "Trang sửa bảng tác giả", notes = "Trang sửa bảng tác giả")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:author:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            AuthorDO author = authorService.get(id);
        model.addAttribute("author", author);
        return "novel/author/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng tác giả", notes = "Trang chi tiết bảng tác giả")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:author:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			AuthorDO author = authorService.get(id);
        model.addAttribute("author", author);
        return "novel/author/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng tác giả", notes = "Thêm bảng tác giả")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:author:add")
    public R save( AuthorDO author) {
        if (authorService.save(author) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng tác giả", notes = "Sửa bảng tác giả")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:author:edit")
    public R update( AuthorDO author) {
            authorService.update(author);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng tác giả", notes = "Xóa bảng tác giả")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:author:remove")
    public R remove( Long id) {
        if (authorService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng tác giả", notes = "Xóa hàng loạt bảng tác giả")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:author:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            authorService.batchRemove(ids);
        return R.ok();
    }

}
