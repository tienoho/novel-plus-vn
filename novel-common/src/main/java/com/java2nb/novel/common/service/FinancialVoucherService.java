package com.java2nb.novel.common.service;

import com.java2nb.novel.common.entity.FinancialVoucherDO;

import java.util.List;
import java.util.Map;

public interface FinancialVoucherService {

    FinancialVoucherDO createRechargeReceipt(String orderNo, String payerName, String payerTaxCode, long grossAmountVnd);

    FinancialVoucherDO createPayoutVoucher(long withdrawalId, String payeeName, String payeeTaxCode, long grossAmountVnd, long withheldTaxVnd);

    FinancialVoucherDO getByVoucherNo(String voucherNo);

    FinancialVoucherDO getAuthorVoucher(String voucherNo, long authorId);

    List<FinancialVoucherDO> listAuthorVouchers(long authorId);

    FinancialVoucherDO getByReference(String referenceType, String referenceId);

    List<FinancialVoucherDO> listVouchers(Map<String, Object> params);

    int countVouchers(Map<String, Object> params);

    byte[] exportVoucherPdf(String voucherNo);

    byte[] exportAuthorVoucherPdf(String voucherNo, long authorId);

    byte[] exportVouchersCsv(List<FinancialVoucherDO> list);

    byte[] exportVouchersJson(List<FinancialVoucherDO> list);

    byte[] exportVoucherJson(FinancialVoucherDO voucher);
}
