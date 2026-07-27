package com.java2nb.novel.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.R;
import com.java2nb.novel.mapper.WalletLedgerMapper;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/novel/finance/ledger")
@RequiredArgsConstructor
public class LedgerAdminController extends BaseController {

    private final WalletLedgerMapper walletLedgerMapper;

    @ResponseBody
    @GetMapping("/audit")
    @RequiresPermissions("novel:pay:pay")
    public R runAudit() {
        List<Map<String, Object>> nonZeroSum = walletLedgerMapper.checkZeroSumLedger();
        List<Map<String, Object>> mismatches = walletLedgerMapper.checkProjectionMismatch();

        boolean healthy = nonZeroSum.isEmpty() && mismatches.isEmpty();

        Map<String, Object> result = new HashMap<>();
        result.put("healthy", healthy);
        result.put("nonZeroSumTransactionsCount", nonZeroSum.size());
        result.put("nonZeroSumTransactions", nonZeroSum);
        result.put("projectionMismatchCount", mismatches.size());
        result.put("projectionMismatches", mismatches);

        return R.ok().put("audit", result);
    }
}
