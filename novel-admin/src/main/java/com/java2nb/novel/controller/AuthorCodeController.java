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


import com.java2nb.novel.domain.AuthorCodeDO;
import com.java2nb.novel.service.AuthorCodeService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Bảng mã mời tác giả
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-05-13 11:29:15
 */

@Controller
@RequestMapping("/novel/authorCode")
public class AuthorCodeController {
    @Autowired
    private AuthorCodeService authorCodeService;

    @GetMapping()
    @RequiresPermissions("novel:authorCode:authorCode")
    String AuthorCode() {
        return "novel/authorCode/authorCode";
    }

    @ApiOperation(value = "Lấy danh sách bảng mã mời tác giả", notes = "Lấy danh sách bảng mã mời tác giả")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:authorCode:authorCode")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<AuthorCodeDO> authorCodeList = authorCodeService.list(query);
        int total = authorCodeService.count(query);
        PageBean pageBean = new PageBean(authorCodeList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm bảng mã mời tác giả", notes = "Trang thêm bảng mã mời tác giả")
    @GetMapping("/add")
    @RequiresPermissions("novel:authorCode:add")
    String add() {
        return "novel/authorCode/add";
    }

    @ApiOperation(value = "Trang sửa bảng mã mời tác giả", notes = "Trang sửa bảng mã mời tác giả")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:authorCode:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            AuthorCodeDO authorCode = authorCodeService.get(id);
        model.addAttribute("authorCode", authorCode);
        return "novel/authorCode/edit";
    }

    @ApiOperation(value = "Trang chi tiết bảng mã mời tác giả", notes = "Trang chi tiết bảng mã mời tác giả")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:authorCode:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			AuthorCodeDO authorCode = authorCodeService.get(id);
        model.addAttribute("authorCode", authorCode);
        return "novel/authorCode/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm bảng mã mời tác giả", notes = "Thêm bảng mã mời tác giả")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:authorCode:add")
    public R save( AuthorCodeDO authorCode) {
        if (authorCodeService.save(authorCode) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa bảng mã mời tác giả", notes = "Sửa bảng mã mời tác giả")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:authorCode:edit")
    public R update( AuthorCodeDO authorCode) {
            authorCodeService.update(authorCode);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa bảng mã mời tác giả", notes = "Xóa bảng mã mời tác giả")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:authorCode:remove")
    public R remove( Long id) {
        if (authorCodeService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt bảng mã mời tác giả", notes = "Xóa hàng loạt bảng mã mời tác giả")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:authorCode:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            authorCodeService.batchRemove(ids);
        return R.ok();
    }

}
