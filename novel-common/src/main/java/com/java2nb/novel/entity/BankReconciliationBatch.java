package com.java2nb.novel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationBatch implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String batchNo;
    private Byte payChannel;
    private Date reconcileDate;
    private Integer totalTransactions;
    private Integer matchedTransactions;
    private Integer mismatchedTransactions;
    private Long totalAmountVnd;
    private String status;
    private Date createTime;
}
