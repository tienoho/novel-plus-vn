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


import com.java2nb.novel.domain.BookContentDO;
import com.java2nb.novel.service.BookContentService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng nội dung tác phẩm
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 19:52:06
 */

@Controller
@RequestMapping("/novel/bookContent")
public class BookContentController {
    @Autowired
    private BookContentService bookContentService;

    @GetMapping()
    @RequiresPermissions("novel:bookContent:bookContent")
    String BookContent() {
        return "novel/bookContent/bookContent";
    }

    @ApiOperation(value = "Lấy danh sách bảng nội dung tác phẩm", notes = "Lấy danh sách bảng nội dung tác phẩm")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:bookContent:bookContent")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<BookContentDO> bookContentList = bookContentService.list(query);
        int total = bookContentService.count(query);
        PageBean pageBean = new PageBean(bookContentList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng nội dung tác phẩm", notes = "Trang thêm bảng nội dung tác phẩm")
    @GetMapping("/add")
    @RequiresPermissions("novel:bookContent:add")
    String add() {
        return "novel/bookContent/add";
    }

    @ApiOperation(value = "Trang sửa bảng nội dung tác phẩm", notes = "Trang sửa bảng nội dung tác phẩm")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:bookContent:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            BookContentDO bookContent = bookContentService.get(id);
        model.addAttribute("bookContent", bookContent);
        return "novel/bookContent/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng nội dung tác phẩm", notes = "Trang chi tiết bảng nội dung tác phẩm")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:bookContent:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			BookContentDO bookContent = bookContentService.get(id);
        model.addAttribute("bookContent", bookContent);
        return "novel/bookContent/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng nội dung tác phẩm", notes = "Thêm bảng nội dung tác phẩm")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:bookContent:add")
    public R save( BookContentDO bookContent) {
        if (bookContentService.save(bookContent) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng nội dung tác phẩm", notes = "Sửa bảng nội dung tác phẩm")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:bookContent:edit")
    public R update( BookContentDO bookContent) {
            bookContentService.update(bookContent);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng nội dung tác phẩm", notes = "Xóa bảng nội dung tác phẩm")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:bookContent:remove")
    public R remove( Long id) {
        if (bookContentService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng nội dung tác phẩm", notes = "Xóa hàng loạt bảng nội dung tác phẩm")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:bookContent:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            bookContentService.batchRemove(ids);
        return R.ok();
    }

}
