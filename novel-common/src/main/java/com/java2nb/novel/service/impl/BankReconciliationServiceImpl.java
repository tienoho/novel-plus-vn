package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.BankReconciliationBatch;
import com.java2nb.novel.entity.BankReconciliationItem;
import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.BankReconciliationMapper;
import com.java2nb.novel.mapper.OrderPayDynamicSqlSupport;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.service.BankReconciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.render.RenderingStrategies;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.select.SelectDSL.select;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankReconciliationServiceImpl implements BankReconciliationService {

    private final BankReconciliationMapper bankReconciliationMapper;
    private final OrderPayMapper orderPayMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public BankReconciliationBatch processStatementFile(byte payChannel, Date reconcileDate, String filename, byte[] fileBytes) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new IllegalArgumentException("File sao kê ngân hàng không được để trống");
        }

        String batchNo = "RECON" + System.currentTimeMillis() + (int)(Math.random() * 900 + 100);
        Date targetDate = reconcileDate != null ? reconcileDate : new Date();

        BankReconciliationBatch batch = BankReconciliationBatch.builder()
            .batchNo(batchNo)
            .payChannel(payChannel)
            .reconcileDate(targetDate)
            .totalTransactions(0)
            .matchedTransactions(0)
            .mismatchedTransactions(0)
            .totalAmountVnd(0L)
            .status("PROCESSING")
            .build();

        bankReconciliationMapper.insertBatch(batch);

        int totalTx = 0;
        int matchedTx = 0;
        int mismatchedTx = 0;
        long totalAmount = 0L;

        List<String> lines = parseLines(fileBytes);
        Set<Long> statementOutTradeNos = new HashSet<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || isHeaderLine(line)) {
                continue;
            }

            String[] parts = line.split("[,;\\t|]");
            if (parts.length < 2) {
                continue;
            }

            Long outTradeNo = parseLong(parts[0]);
            String bankTradeNo = parts.length > 1 ? parts[1].trim() : "BANK_" + System.currentTimeMillis();
            Integer amountVnd = parts.length > 2 ? parseInt(parts[2]) : null;

            if (outTradeNo == null && parts.length > 2) {
                // Try reverse index
                outTradeNo = parseLong(parts[1]);
                bankTradeNo = parts[0].trim();
            }

            if (amountVnd == null) {
                amountVnd = 0;
            }

            totalTx++;
            totalAmount += amountVnd;
            if (outTradeNo != null) {
                statementOutTradeNos.add(outTradeNo);
            }

            String matchStatus;
            String discrepancyReason = null;

            if (outTradeNo == null) {
                matchStatus = "NOT_FOUND_IN_SYSTEM";
                discrepancyReason = "Không thể bóc tách mã đơn hệ thống từ dòng sao kê";
                mismatchedTx++;
            } else {
                OrderPay orderPay = findOrderPay(outTradeNo);
                if (orderPay == null) {
                    matchStatus = "NOT_FOUND_IN_SYSTEM";
                    discrepancyReason = "Không tìm thấy đơn hàng " + outTradeNo + " trong hệ thống";
                    mismatchedTx++;
                } else if (orderPay.getTotalAmount() != null && !orderPay.getTotalAmount().equals(amountVnd)) {
                    matchStatus = "AMOUNT_MISMATCH";
                    discrepancyReason = "Số tiền sao kê (" + amountVnd + " VND) lệch với hệ thống (" + orderPay.getTotalAmount() + " VND)";
                    mismatchedTx++;
                } else {
                    matchStatus = "MATCHED";
                    matchedTx++;
                }
            }

            BankReconciliationItem item = BankReconciliationItem.builder()
                .batchId(batch.getId())
                .outTradeNo(outTradeNo)
                .bankTradeNo(bankTradeNo)
                .amountVnd(amountVnd)
                .matchStatus(matchStatus)
                .discrepancyReason(discrepancyReason)
                .build();

            bankReconciliationMapper.insertItem(item);
        }

        batch.setTotalTransactions(totalTx);
        batch.setMatchedTransactions(matchedTx);
        batch.setMismatchedTransactions(mismatchedTx);
        batch.setTotalAmountVnd(totalAmount);
        batch.setStatus("COMPLETED");

        bankReconciliationMapper.updateBatchSummary(batch);
        log.info("Xử lý thành công lô đối soát {}, matched: {}/{}, mismatched: {}", batchNo, matchedTx, totalTx, mismatchedTx);
        return batch;
    }

    @Override
    public List<BankReconciliationBatch> listBatches(Map<String, Object> params) {
        return bankReconciliationMapper.listBatches(params);
    }

    @Override
    public int countBatches(Map<String, Object> params) {
        return bankReconciliationMapper.countBatches(params);
    }

    @Override
    public BankReconciliationBatch getBatchById(long id) {
        BankReconciliationBatch batch = bankReconciliationMapper.selectBatchById(id);
        if (batch == null) {
            throw new IllegalArgumentException("Không tìm thấy lô đối soát id: " + id);
        }
        return batch;
    }

    @Override
    public List<BankReconciliationItem> getBatchItems(long batchId) {
        return bankReconciliationMapper.listItemsByBatchId(batchId);
    }

    private OrderPay findOrderPay(long outTradeNo) {
        SelectStatementProvider selectStatement = select(
            OrderPayDynamicSqlSupport.id,
            OrderPayDynamicSqlSupport.outTradeNo,
            OrderPayDynamicSqlSupport.payStatus,
            OrderPayDynamicSqlSupport.totalAmount,
            OrderPayDynamicSqlSupport.accountAmount,
            OrderPayDynamicSqlSupport.userId,
            OrderPayDynamicSqlSupport.payChannel
        )
        .from(OrderPayDynamicSqlSupport.orderPay)
        .where(OrderPayDynamicSqlSupport.outTradeNo, isEqualTo(outTradeNo))
        .build()
        .render(RenderingStrategies.MYBATIS3);
        return orderPayMapper.selectOne(selectStatement).orElse(null);
    }

    private List<String> parseLines(byte[] fileBytes) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(fileBytes), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            log.error("Lỗi đọc file sao kê đối soát", e);
        }
        return lines;
    }

    private boolean isHeaderLine(String line) {
        String lower = line.toLowerCase();
        return lower.contains("outtradeno") || lower.contains("banktradeno") || lower.contains("amount") || lower.contains("mã đơn");
    }

    private Long parseLong(String val) {
        if (val == null) return null;
        try {
            return Long.parseLong(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }

    private Integer parseInt(String val) {
        if (val == null) return null;
        try {
            return Integer.parseInt(val.replaceAll("[^0-9]", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
