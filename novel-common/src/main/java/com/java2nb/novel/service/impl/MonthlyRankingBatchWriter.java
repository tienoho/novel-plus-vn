package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.service.gamification.MonthlyRankRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/** Ghi entry và checkpoint trong cùng một transaction độc lập để retry không bị xé batch. */
@Service
@RequiredArgsConstructor
public class MonthlyRankingBatchWriter {

    private final MonthlyRankingMapper monthlyRankingMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void append(String jobType, String scopeType, String scopeKey, String ownerInstance,
                       List<MonthlyRankRow> rows, long nextOffset, Date heartbeatAt) {
        if (rows.isEmpty()) {
            return;
        }
        if (monthlyRankingMapper.insertRankEntries(rows) != rows.size()) {
            throw new IllegalStateException("Không thể ghi đủ batch snapshot xếp hạng");
        }
        if (monthlyRankingMapper.advanceJobCheckpoint(jobType, scopeType, scopeKey, ownerInstance,
            Long.toString(nextOffset), rows.size(), heartbeatAt) != 1) {
            throw new IllegalStateException("Mất quyền sở hữu job khi ghi checkpoint snapshot");
        }
    }
}
