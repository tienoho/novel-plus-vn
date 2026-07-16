package com.java2nb.system.controller;

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


import com.java2nb.system.domain.RoleDataPermDO;
import com.java2nb.system.service.RoleDataPermService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Quan hệ giữa vai trò và quyền dữ liệu
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2019-11-25 11:32:49
 */

@Controller
@RequestMapping("/system/roleDataPerm")
public class RoleDataPermController {
    @Autowired
    private RoleDataPermService roleDataPermService;

    @GetMapping()
    @RequiresPermissions("system:roleDataPerm:roleDataPerm")
    String RoleDataPerm() {
        return "system/roleDataPerm/roleDataPerm";
    }

    @ApiOperation(value = "Lấy danh sách quan hệ giữa vai trò và quyền dữ liệu", notes = "Lấy danh sách quan hệ giữa vai trò và quyền dữ liệu")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("system:roleDataPerm:roleDataPerm")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<RoleDataPermDO> roleDataPermList = roleDataPermService.list(query);
        int total = roleDataPermService.count(query);
        PageBean pageBean = new PageBean(roleDataPermList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm quan hệ giữa vai trò và quyền dữ liệu", notes = "Trang thêm quan hệ giữa vai trò và quyền dữ liệu")
    @GetMapping("/add")
    @RequiresPermissions("system:roleDataPerm:add")
    String add() {
        return "system/roleDataPerm/add";
    }

    @ApiOperation(value = "Trang sửa quan hệ giữa vai trò và quyền dữ liệu", notes = "Trang sửa quan hệ giữa vai trò và quyền dữ liệu")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("system:roleDataPerm:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            RoleDataPermDO roleDataPerm = roleDataPermService.get(id);
        model.addAttribute("roleDataPerm", roleDataPerm);
        return "system/roleDataPerm/edit";
    }

    @ApiOperation(value = "Trang chi tiết quan hệ giữa vai trò và quyền dữ liệu", notes = "Trang chi tiết quan hệ giữa vai trò và quyền dữ liệu")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("system:roleDataPerm:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			RoleDataPermDO roleDataPerm = roleDataPermService.get(id);
        model.addAttribute("roleDataPerm", roleDataPerm);
        return "system/roleDataPerm/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm quan hệ giữa vai trò và quyền dữ liệu", notes = "Thêm quan hệ giữa vai trò và quyền dữ liệu")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("system:roleDataPerm:add")
    public R save( RoleDataPermDO roleDataPerm) {
        if (roleDataPermService.save(roleDataPerm) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa quan hệ giữa vai trò và quyền dữ liệu", notes = "Sửa quan hệ giữa vai trò và quyền dữ liệu")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("system:roleDataPerm:edit")
    public R update( RoleDataPermDO roleDataPerm) {
            roleDataPermService.update(roleDataPerm);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa quan hệ giữa vai trò và quyền dữ liệu", notes = "Xóa quan hệ giữa vai trò và quyền dữ liệu")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("system:roleDataPerm:remove")
    public R remove( Long id) {
        if (roleDataPermService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt quan hệ giữa vai trò và quyền dữ liệu", notes = "Xóa hàng loạt quan hệ giữa vai trò và quyền dữ liệu")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("system:roleDataPerm:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            roleDataPermService.batchRemove(ids);
        return R.ok();
    }

}
