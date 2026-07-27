package com.java2nb.novel.controller;

import com.java2nb.common.controller.BaseController;
import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.entity.BankReconciliationBatch;
import com.java2nb.novel.entity.BankReconciliationItem;
import com.java2nb.novel.service.BankReconciliationService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/novel/finance/reconciliation")
@RequiredArgsConstructor
public class BankReconciliationAdminController extends BaseController {

    private final BankReconciliationService reconciliationService;

    @ResponseBody
    @PostMapping("/upload")
    @RequiresPermissions("novel:pay:pay")
    public R uploadStatement(@RequestParam("payChannel") Byte payChannel,
                             @RequestParam(value = "file", required = false) MultipartFile file,
                             @RequestParam(value = "content", required = false) String content) {
        try {
            byte[] fileBytes;
            String filename = "statement.csv";
            if (file != null && !file.isEmpty()) {
                fileBytes = file.getBytes();
                filename = file.getOriginalFilename();
            } else if (content != null && !content.trim().isEmpty()) {
                fileBytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            } else {
                return R.error("Thiếu file sao kê hoặc nội dung sao kê đối soát");
            }

            BankReconciliationBatch batch = reconciliationService.processStatementFile(payChannel, new Date(), filename, fileBytes);
            return R.ok().put("data", batch);
        } catch (Exception e) {
            return R.error("Lỗi đối soát sao kê: " + e.getMessage());
        }
    }

    @ResponseBody
    @GetMapping("/batches")
    @RequiresPermissions("novel:pay:pay")
    public R listBatches(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        List<BankReconciliationBatch> list = reconciliationService.listBatches(query);
        int total = reconciliationService.countBatches(query);
        return R.ok().put("data", new PageBean(list, total));
    }

    @ResponseBody
    @GetMapping("/batches/{id}")
    @RequiresPermissions("novel:pay:pay")
    public R getBatchDetail(@PathVariable("id") Long id) {
        BankReconciliationBatch batch = reconciliationService.getBatchById(id);
        List<BankReconciliationItem> items = reconciliationService.getBatchItems(id);
        return R.ok().put("batch", batch).put("items", items);
    }
}
