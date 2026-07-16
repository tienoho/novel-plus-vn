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


import com.java2nb.novel.domain.PayDO;
import com.java2nb.novel.service.PayService;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;

/**
 * Đơn nạp tiền
 *
 * @author xiongxy
 * @email 1179705413@qq.com
 * @date 2020-12-01 03:49:57
 */

@Controller
@RequestMapping("/novel/pay")
public class PayController {
    @Autowired
    private PayService payService;

    @GetMapping()
    @RequiresPermissions("novel:pay:pay")
    String Pay() {
        return "novel/pay/pay";
    }

    @ApiOperation(value = "Lấy danh sách đơn nạp tiền", notes = "Lấy danh sách đơn nạp tiền")
    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:pay:pay")
    public R list(@RequestParam Map<String, Object> params) {
        // Truy vấn dữ liệu danh sách
        Query query = new Query(params);
        List<PayDO> payList = payService.list(query);
        int total = payService.count(query);
        PageBean pageBean = new PageBean(payList, total);
        return R.ok().put("data", pageBean);
    }

    @ApiOperation(value = "Trang thêm đơn nạp tiền", notes = "Trang thêm đơn nạp tiền")
    @GetMapping("/add")
    @RequiresPermissions("novel:pay:add")
    String add() {
        return "novel/pay/add";
    }

    @ApiOperation(value = "Trang sửa đơn nạp tiền", notes = "Trang sửa đơn nạp tiền")
    @GetMapping("/edit/{id}")
    @RequiresPermissions("novel:pay:edit")
    String edit(@PathVariable("id") Long id, Model model) {
            PayDO pay = payService.get(id);
        model.addAttribute("pay", pay);
        return "novel/pay/edit";
    }

    @ApiOperation(value = "Trang chi tiết đơn nạp tiền", notes = "Trang chi tiết đơn nạp tiền")
    @GetMapping("/detail/{id}")
    @RequiresPermissions("novel:pay:detail")
    String detail(@PathVariable("id") Long id, Model model) {
			PayDO pay = payService.get(id);
        model.addAttribute("pay", pay);
        return "novel/pay/detail";
    }

    /**
     * Lưu
     */
    @ApiOperation(value = "Thêm đơn nạp tiền", notes = "Thêm đơn nạp tiền")
    @ResponseBody
    @PostMapping("/save")
    @RequiresPermissions("novel:pay:add")
    public R save( PayDO pay) {
        if (payService.save(pay) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Sửa
     */
    @ApiOperation(value = "Sửa đơn nạp tiền", notes = "Sửa đơn nạp tiền")
    @ResponseBody
    @RequestMapping("/update")
    @RequiresPermissions("novel:pay:edit")
    public R update( PayDO pay) {
            payService.update(pay);
        return R.ok();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa đơn nạp tiền", notes = "Xóa đơn nạp tiền")
    @PostMapping("/remove")
    @ResponseBody
    @RequiresPermissions("novel:pay:remove")
    public R remove( Long id) {
        if (payService.remove(id) > 0) {
            return R.ok();
        }
        return R.error();
    }

    /**
     * Xóa
     */
    @ApiOperation(value = "Xóa hàng loạt đơn nạp tiền", notes = "Xóa hàng loạt đơn nạp tiền")
    @PostMapping("/batchRemove")
    @ResponseBody
    @RequiresPermissions("novel:pay:batchRemove")
    public R remove(@RequestParam("ids[]") Long[] ids) {
            payService.batchRemove(ids);
        return R.ok();
    }

}
