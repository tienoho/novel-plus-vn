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


import com.java2nb.novel.domain.CategoryDO;
import com.java2nb.novel.service.CategoryService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng danh mục tin tức
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 10:03:41
 */

@Controller
@RequestMapping("/novel/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @GetMapping()
    @RequiresPermissions("novel:category:category")
    String Category() {
        return "novel/category/category";
    }

    @ApiOperation(value = "Lấy danh sách bảng danh mục tin tức", notes = "Lấy danh sách bảng danh mục tin tức")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:category:category")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<CategoryDO> categoryList = categoryService.list(query);
        int total = categoryService.count(query);
        PageBean pageBean = new PageBean(categoryList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng danh mục tin tức", notes = "Trang thêm bảng danh mục tin tức")
    @GetMapping("/add")
    @RequiresPermissions("novel:category:add")
    String add() {
        return "novel/category/add";
    }

    @ApiOperation(value = "Trang sửa bảng danh mục tin tức", notes = "Trang sửa bảng danh mục tin tức")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:category:edit")
    String edit(@PathVariable("id") Integer id, Model model) {
            CategoryDO category = categoryService.get(id);
        model.addAttribute("category", category);
        return "novel/category/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng danh mục tin tức", notes = "Trang chi tiết bảng danh mục tin tức")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:category:detail")
    String detail(@PathVariable("id") Integer id, Model model) {
			CategoryDO category = categoryService.get(id);
        model.addAttribute("category", category);
        return "novel/category/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng danh mục tin tức", notes = "Thêm bảng danh mục tin tức")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:category:add")
    public R save( CategoryDO category) {
        if (categoryService.save(category) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng danh mục tin tức", notes = "Sửa bảng danh mục tin tức")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:category:edit")
    public R update( CategoryDO category) {
            categoryService.update(category);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng danh mục tin tức", notes = "Xóa bảng danh mục tin tức")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:category:remove")
    public R remove( Integer id) {
        if (categoryService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng danh mục tin tức", notes = "Xóa hàng loạt bảng danh mục tin tức")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:category:batchRemove")
    public R remove(@RequestParam("ids[]") Integer[] ids) {
            categoryService.batchRemove(ids);
        return R.ok();
    }

}
