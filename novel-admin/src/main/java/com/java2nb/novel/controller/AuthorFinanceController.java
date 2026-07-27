package com.java2nb.novel.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.service.AuthorFinanceReviewService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/novel/authorFinance")
public class AuthorFinanceController extends BaseController {

    private final AuthorFinanceReviewService service;

    public AuthorFinanceController(AuthorFinanceReviewService service) {
        this.service = service;
    }

    @GetMapping()
    @RequiresPermissions("novel:authorFinance:view")
    public String index() {
        return "novel/authorFinance/authorFinance";
    }

    @ResponseBody
    @GetMapping("/kyc/list")
    @RequiresPermissions("novel:authorFinance:view")
    public R listKyc(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listKyc(query), service.countKyc(query)));
    }

    @ResponseBody
    @GetMapping("/kyc/{id}")
    @RequiresPermissions("novel:authorFinance:pii")
    public R getKyc(@PathVariable long id) {
        return R.ok().put("data", service.getKycDetail(id, getUserId()));
    }

    @ResponseBody
    @PostMapping("/kyc/{id}/approve")
    @RequiresPermissions("novel:authorFinance:kyc")
    public R approveKyc(@PathVariable long id, @RequestParam int expectedVersion) {
        service.approveKyc(id, expectedVersion, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/kyc/{id}/reject")
    @RequiresPermissions("novel:authorFinance:kyc")
    public R rejectKyc(@PathVariable long id, @RequestParam int expectedVersion, @RequestParam String reason) {
        service.rejectKyc(id, expectedVersion, reason, getUserId());
        return R.ok();
    }

    @ResponseBody
    @GetMapping("/withdrawals/list")
    @RequiresPermissions("novel:authorFinance:view")
    public R listWithdrawals(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        return R.ok().put("data", new PageBean(service.listWithdrawals(query), service.countWithdrawals(query)));
    }

    @ResponseBody
    @GetMapping("/withdrawals/{id}")
    @RequiresPermissions("novel:authorFinance:view")
    public R getWithdrawal(@PathVariable long id) {
        return R.ok().put("data", service.getWithdrawal(id));
    }

    @ResponseBody
    @GetMapping("/withdrawals/{id}/payout-details")
    @RequiresPermissions("novel:authorFinance:pii")
    public R getWithdrawalPayoutDetail(@PathVariable long id) {
        return R.ok().put("data", service.getWithdrawalPayoutDetail(id, getUserId()));
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/approve")
    @RequiresPermissions("novel:authorFinance:payout")
    public R approveWithdrawal(@PathVariable long id, @RequestParam long expectedVersion,
                               @RequestParam(defaultValue = "0") long withheldTaxVnd) {
        service.approveWithdrawal(id, expectedVersion, withheldTaxVnd, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/reject")
    @RequiresPermissions("novel:authorFinance:payout")
    public R rejectWithdrawal(@PathVariable long id, @RequestParam long expectedVersion,
                              @RequestParam String reason) {
        service.rejectWithdrawal(id, expectedVersion, reason, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/processing")
    @RequiresPermissions("novel:authorFinance:payout")
    public R markProcessing(@PathVariable long id, @RequestParam long expectedVersion) {
        service.markProcessing(id, expectedVersion, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/paid")
    @RequiresPermissions("novel:authorFinance:payout")
    public R markPaid(@PathVariable long id, @RequestParam long expectedVersion,
                      @RequestParam String providerReference) {
        service.markPaid(id, expectedVersion, providerReference, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/failed")
    @RequiresPermissions("novel:authorFinance:payout")
    public R markFailed(@PathVariable long id, @RequestParam long expectedVersion, @RequestParam String reason) {
        service.markFailed(id, expectedVersion, reason, getUserId());
        return R.ok();
    }

    @ResponseBody
    @PostMapping("/withdrawals/{id}/auto-payout")
    @RequiresPermissions("novel:authorFinance:payout")
    public R autoPayout(@PathVariable long id) {
        service.executeAutoPayout(id, getUserId());
        return R.ok();
    }
}
