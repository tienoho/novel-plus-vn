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


import com.java2nb.novel.domain.BookIndexDO;
import com.java2nb.novel.service.BookIndexService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng mục lục tác phẩm
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 19:51:54
 */

@Controller
@RequestMapping("/novel/bookIndex")
public class BookIndexController {
    @Autowired
    private BookIndexService bookIndexService;

    @GetMapping()
    @RequiresPermissions("novel:bookIndex:bookIndex")
    String BookIndex() {
        return "novel/bookIndex/bookIndex";
    }

    @ApiOperation(value = "Lấy danh sách bảng mục lục tác phẩm", notes = "Lấy danh sách bảng mục lục tác phẩm")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:bookIndex:bookIndex")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<BookIndexDO> bookIndexList = bookIndexService.list(query);
        int total = bookIndexService.count(query);
        PageBean pageBean = new PageBean(bookIndexList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng mục lục tác phẩm", notes = "Trang thêm bảng mục lục tác phẩm")
    @GetMapping("/add")
    @RequiresPermissions("novel:bookIndex:add")
    String add() {
        return "novel/bookIndex/add";
    }

    @ApiOperation(value = "Trang sửa bảng mục lục tác phẩm", notes = "Trang sửa bảng mục lục tác phẩm")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:bookIndex:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            BookIndexDO bookIndex = bookIndexService.get(id);
        model.addAttribute("bookIndex", bookIndex);
        return "novel/bookIndex/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng mục lục tác phẩm", notes = "Trang chi tiết bảng mục lục tác phẩm")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:bookIndex:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			BookIndexDO bookIndex = bookIndexService.get(id);
        model.addAttribute("bookIndex", bookIndex);
        return "novel/bookIndex/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng mục lục tác phẩm", notes = "Thêm bảng mục lục tác phẩm")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:bookIndex:add")
    public R save( BookIndexDO bookIndex) {
        if (bookIndexService.save(bookIndex) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng mục lục tác phẩm", notes = "Sửa bảng mục lục tác phẩm")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:bookIndex:edit")
    public R update( BookIndexDO bookIndex) {
            bookIndexService.update(bookIndex);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng mục lục tác phẩm", notes = "Xóa bảng mục lục tác phẩm")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:bookIndex:remove")
    public R remove( Long id) {
        if (bookIndexService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng mục lục tác phẩm", notes = "Xóa hàng loạt bảng mục lục tác phẩm")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:bookIndex:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            bookIndexService.batchRemove(ids);
        return R.ok();
    }

}
