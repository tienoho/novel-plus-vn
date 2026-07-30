package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.service.impl.MonthlyRankingBatchWriter;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MonthlyRankingBatchWriterTest {

    @Test
    void writesEntriesBeforeAdvancingCheckpoint() {
        MonthlyRankingMapper mapper = mock(MonthlyRankingMapper.class);
        MonthlyRankingBatchWriter writer = new MonthlyRankingBatchWriter(mapper);
        MonthlyRankRow row = new MonthlyRankRow();
        List<MonthlyRankRow> rows = List.of(row);
        Date heartbeat = new Date(1_000L);
        when(mapper.insertRankEntries(rows)).thenReturn(1);
        when(mapper.advanceJobCheckpoint("SEASON_SNAPSHOT", "SEASON", "71", "owner",
            "1", 1, heartbeat)).thenReturn(1);

        writer.append("SEASON_SNAPSHOT", "SEASON", "71", "owner", rows, 1, heartbeat);

        var order = inOrder(mapper);
        order.verify(mapper).insertRankEntries(rows);
        order.verify(mapper).advanceJobCheckpoint(
            "SEASON_SNAPSHOT", "SEASON", "71", "owner", "1", 1, heartbeat);
    }

    @Test
    void rejectsAHeartbeatThatLostJobOwnership() {
        MonthlyRankingMapper mapper = mock(MonthlyRankingMapper.class);
        MonthlyRankingBatchWriter writer = new MonthlyRankingBatchWriter(mapper);
        List<MonthlyRankRow> rows = List.of(new MonthlyRankRow());
        when(mapper.insertRankEntries(rows)).thenReturn(1);

        assertThatThrownBy(() -> writer.append(
            "SEASON_SNAPSHOT", "SEASON", "71", "owner", rows, 1, new Date()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mất quyền sở hữu");
    }
}
