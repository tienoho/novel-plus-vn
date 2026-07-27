package com.java2nb.novel.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.OrderRefund;
import com.java2nb.novel.service.RefundService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/novel/finance/refunds")
@RequiredArgsConstructor
public class FinanceRefundController extends BaseController {

    private final RefundService refundService;

    @ResponseBody
    @GetMapping("/list")
    @RequiresPermissions("novel:pay:pay")
    public R list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        List<OrderRefund> list = refundService.listRefunds(query);
        int total = refundService.countRefunds(query);
        return R.ok().put("data", new PageBean(list, total));
    }

    @ResponseBody
    @PostMapping("/{id}/approve")
    @RequiresPermissions("novel:pay:edit")
    public R approve(@PathVariable("id") Long id) {
        try {
            OrderRefund refund = refundService.approveRefund(id, getUserId());
            return R.ok().put("data", refund);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @PostMapping("/{id}/confirm")
    @RequiresPermissions("novel:pay:edit")
    public R confirm(@PathVariable("id") Long id,
                     @RequestParam("providerReference") String providerReference) {
        try {
            OrderRefund refund = refundService.confirmRefund(id, providerReference, getUserId());
            return R.ok().put("data", refund);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @PostMapping("/{id}/fail")
    @RequiresPermissions("novel:pay:edit")
    public R fail(@PathVariable("id") Long id, @RequestParam("reason") String reason) {
        try {
            OrderRefund refund = refundService.failRefund(id, reason, getUserId());
            return R.ok().put("data", refund);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @PostMapping("/{id}/reject")
    @RequiresPermissions("novel:pay:edit")
    public R reject(@PathVariable("id") Long id, @RequestParam(value = "reason", required = false) String reason) {
        try {
            OrderRefund refund = refundService.rejectRefund(id, reason, getUserId());
            return R.ok().put("data", refund);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }

    @ResponseBody
    @PostMapping("/chargeback/record")
    @RequiresPermissions("novel:pay:edit")
    public R recordChargeback(@RequestParam("outTradeNo") Long outTradeNo,
                             @RequestParam("refundAmountVnd") Integer refundAmountVnd,
                             @RequestParam("reason") String reason) {
        try {
            OrderRefund refund = refundService.recordChargeback(outTradeNo, refundAmountVnd, reason, getUserId());
            return R.ok().put("data", refund);
        } catch (Exception e) {
            return R.error(e.getMessage());
        }
    }
}
