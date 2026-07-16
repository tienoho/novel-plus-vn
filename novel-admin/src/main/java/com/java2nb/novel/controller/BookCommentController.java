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


import com.java2nb.novel.domain.BookCommentDO;
import com.java2nb.novel.service.BookCommentService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng bình luận tác phẩm
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2023-04-14 21:59:28
 */

@Controller
@RequestMapping("/novel/bookComment")
public class BookCommentController {
    @Autowired
    private BookCommentService bookCommentService;

    @GetMapping()
    @RequiresPermissions("novel:bookComment:bookComment")
    String BookComment() {
        return "novel/bookComment/bookComment";
    }

    @ApiOperation(value = "Lấy danh sách bảng bình luận tác phẩm", notes = "Lấy danh sách bảng bình luận tác phẩm")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:bookComment:bookComment")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<BookCommentDO> bookCommentList = bookCommentService.list(query);
        int total = bookCommentService.count(query);
        PageBean pageBean = new PageBean(bookCommentList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng bình luận tác phẩm", notes = "Trang thêm bảng bình luận tác phẩm")
    @GetMapping("/add")
    @RequiresPermissions("novel:bookComment:add")
    String add() {
        return "novel/bookComment/add";
    }

    @ApiOperation(value = "Trang sửa bảng bình luận tác phẩm", notes = "Trang sửa bảng bình luận tác phẩm")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:bookComment:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            BookCommentDO bookComment = bookCommentService.get(id);
        model.addAttribute("bookComment", bookComment);
        return "novel/bookComment/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng bình luận tác phẩm", notes = "Trang chi tiết bảng bình luận tác phẩm")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:bookComment:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			BookCommentDO bookComment = bookCommentService.get(id);
        model.addAttribute("bookComment", bookComment);
        return "novel/bookComment/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng bình luận tác phẩm", notes = "Thêm bảng bình luận tác phẩm")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:bookComment:add")
    public R save( BookCommentDO bookComment) {
        if (bookCommentService.save(bookComment) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng bình luận tác phẩm", notes = "Sửa bảng bình luận tác phẩm")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:bookComment:edit")
    public R update( BookCommentDO bookComment) {
            bookCommentService.update(bookComment);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng bình luận tác phẩm", notes = "Xóa bảng bình luận tác phẩm")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:bookComment:remove")
    public R remove( Long id) {
        if (bookCommentService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng bình luận tác phẩm", notes = "Xóa hàng loạt bảng bình luận tác phẩm")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:bookComment:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            bookCommentService.batchRemove(ids);
        return R.ok();
    }

}
