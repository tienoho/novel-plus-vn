package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.BankReconciliationBatch;
import com.java2nb.novel.entity.BankReconciliationItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface BankReconciliationMapper {

    int insertBatch(BankReconciliationBatch batch);

    int insertItem(BankReconciliationItem item);

    BankReconciliationBatch selectBatchById(@Param("id") Long id);

    BankReconciliationBatch selectBatchByNo(@Param("batchNo") String batchNo);

    List<BankReconciliationBatch> listBatches(Map<String, Object> params);

    int countBatches(Map<String, Object> params);

    List<BankReconciliationItem> listItemsByBatchId(@Param("batchId") Long batchId);

    int updateBatchSummary(BankReconciliationBatch batch);
}
