package com.java2nb.novel.controller;

import com.java2nb.common.utils.PageBean;
import com.java2nb.common.utils.Query;
import com.java2nb.common.utils.R;
import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.FinancialVoucherService;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/novel/reports/vouchers")
@RequiredArgsConstructor
public class FinancialVoucherAdminController {

    private final FinancialVoucherService voucherService;

    @GetMapping("/list")
    @RequiresPermissions("novel:reports:vouchers")
    public PageBean list(@RequestParam Map<String, Object> params) {
        Query query = new Query(params);
        List<FinancialVoucherDO> list = voucherService.listVouchers(query);
        int total = voucherService.countVouchers(query);
        return new PageBean(list, total);
    }

    @GetMapping("/{voucherNo}")
    @RequiresPermissions("novel:reports:vouchers")
    public R getByVoucherNo(@PathVariable String voucherNo) {
        FinancialVoucherDO voucher = voucherService.getByVoucherNo(voucherNo);
        if (voucher == null) {
            return R.error("Khong tim thay chung tu: " + voucherNo);
        }
        return R.ok().put("voucher", voucher);
    }

    @GetMapping("/{voucherNo}/pdf")
    @RequiresPermissions("novel:reports:vouchers")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable String voucherNo) {
        byte[] pdfBytes = voucherService.exportVoucherPdf(voucherNo);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + voucherNo + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/export/csv")
    @RequiresPermissions("novel:reports:vouchers")
    public ResponseEntity<byte[]> exportCsv(@RequestParam Map<String, Object> params) {
        List<FinancialVoucherDO> list = voucherService.listVouchers(params);
        byte[] csvData = voucherService.exportVouchersCsv(list);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vouchers.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvData);
    }

    @GetMapping("/export/json")
    @RequiresPermissions("novel:reports:vouchers")
    public ResponseEntity<byte[]> exportJson(@RequestParam Map<String, Object> params) {
        List<FinancialVoucherDO> list = voucherService.listVouchers(params);
        byte[] jsonData = voucherService.exportVouchersJson(list);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"vouchers.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonData);
    }

    @GetMapping("/{voucherNo}/json")
    @RequiresPermissions("novel:reports:vouchers")
    public ResponseEntity<byte[]> getVoucherJson(@PathVariable String voucherNo) {
        FinancialVoucherDO voucher = voucherService.getByVoucherNo(voucherNo);
        if (voucher == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] jsonData = voucherService.exportVoucherJson(voucher);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonData);
    }
}
