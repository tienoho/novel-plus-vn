package com.java2nb.novel.controller;

import com.java2nb.novel.common.entity.FinancialVoucherDO;
import com.java2nb.novel.common.service.FinancialVoucherService;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.entity.Author;
import com.java2nb.novel.service.AuthorService;
import io.github.xxyopen.model.resp.RestResult;
import com.java2nb.novel.core.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for author finance receipts and payout vouchers in novel-front.
 */
@Slf4j
@RestController
@RequestMapping("/author/finance/receipts")
@RequiredArgsConstructor
public class AuthorFinanceReceiptsController extends BaseController {

    private final FinancialVoucherService voucherService;
    private final AuthorService authorService;

    @GetMapping
    public RestResult<List<FinancialVoucherDO>> listReceipts(HttpServletRequest request) {
        Author author = checkAuthor(request);
        return RestResult.ok(voucherService.listAuthorVouchers(author.getId()));
    }

    @GetMapping("/{voucherNo}")
    public RestResult<FinancialVoucherDO> getReceipt(@PathVariable("voucherNo") String voucherNo, HttpServletRequest request) {
        Author author = checkAuthor(request);
        FinancialVoucherDO voucher = voucherService.getAuthorVoucher(voucherNo, author.getId());
        if (voucher == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_VOUCHER_NOT_FOUND);
        }
        return RestResult.ok(voucher);
    }

    @GetMapping("/{voucherNo}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable("voucherNo") String voucherNo, HttpServletRequest request) {
        Author author = checkAuthor(request);
        byte[] pdfBytes = voucherService.exportAuthorVoucherPdf(voucherNo, author.getId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + voucherNo + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }

    @GetMapping("/{voucherNo}/json")
    public ResponseEntity<byte[]> getVoucherJson(@PathVariable("voucherNo") String voucherNo, HttpServletRequest request) {
        Author author = checkAuthor(request);
        FinancialVoucherDO voucher = voucherService.getAuthorVoucher(voucherNo, author.getId());
        if (voucher == null) {
            return ResponseEntity.notFound().build();
        }
        byte[] jsonData = voucherService.exportVoucherJson(voucher);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonData);
    }

    @GetMapping("/export/csv")
    public ResponseEntity<byte[]> exportCsv(HttpServletRequest request) {
        Author author = checkAuthor(request);
        List<FinancialVoucherDO> list = voucherService.listAuthorVouchers(author.getId());
        byte[] csvBytes = voucherService.exportVouchersCsv(list);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"author_receipts.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csvBytes);
    }

    private Author checkAuthor(HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        Author author = authorService.queryAuthor(userDetails.getId());
        if (author == null) {
            throw new BusinessException(ResponseStatus.AUTHOR_STATUS_FORBIDDEN);
        }
        return author;
    }
}
