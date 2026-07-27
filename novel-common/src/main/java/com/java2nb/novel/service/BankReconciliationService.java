package com.java2nb.novel.service;

import com.java2nb.novel.entity.BankReconciliationBatch;
import com.java2nb.novel.entity.BankReconciliationItem;

import java.util.Date;
import java.util.List;
import java.util.Map;

public interface BankReconciliationService {

    BankReconciliationBatch processStatementFile(byte payChannel, Date reconcileDate, String filename, byte[] fileBytes);

    List<BankReconciliationBatch> listBatches(Map<String, Object> params);

    int countBatches(Map<String, Object> params);

    BankReconciliationBatch getBatchById(long id);

    List<BankReconciliationItem> getBatchItems(long batchId);
}
