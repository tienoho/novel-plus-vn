package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.service.impl.MonthlyRankingServiceImpl;
import com.java2nb.novel.service.impl.MonthlyRankingBatchWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.reset;
import org.mockito.ArgumentCaptor;

class MonthlyRankingServiceImplTest {

    private static final long SEASON_ID = 71L;
    private static final Date CUTOFF = Date.from(Instant.parse("2026-09-01T00:00:00Z"));

    private MonthlyRankingMapper mapper;
    private MonthlyRankingBatchWriter batchWriter;
    private MonthlyRankingServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(MonthlyRankingMapper.class);
        batchWriter = mock(MonthlyRankingBatchWriter.class);
        service = new MonthlyRankingServiceImpl(
            mapper, batchWriter, Clock.fixed(CUTOFF.toInstant(), ZoneOffset.UTC));
    }

    @Test
    void onlyOneCallerOwnsTheClosePhase() {
        MonthlySeasonRow open = season("OPEN", 4L, null);
        MonthlySeasonRow closing = season("CLOSING", 5L, null);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(open, closing);
        when(mapper.claimSeasonClosing(SEASON_ID, 4L, CUTOFF)).thenReturn(1);

        SeasonPhaseResult winner = service.closeSeason(SEASON_ID, CUTOFF);
        SeasonPhaseResult loser = service.closeSeason(SEASON_ID, CUTOFF);

        assertThat(winner.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(loser.outcome()).isEqualTo(SeasonPhaseResult.Outcome.NOT_OWNER);
    }

    @Test
    void retryContinuesAtCommittedCheckpointAndSealsDeterministicHash() throws Exception {
        Date closingAt = Date.from(CUTOFF.toInstant().minusSeconds(120));
        MonthlySeasonRow closing = season("CLOSING", 5L, null);
        closing.setClosingAt(closingAt);
        closing.setVoteCutoffAt(CUTOFF);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(closing, closing, closing);
        when(mapper.insertJobRunIgnore(anyString(), anyString(), anyString(), anyString(), eq(CUTOFF)))
            .thenReturn(1, 0);
        when(mapper.claimStaleJobRun(anyString(), anyString(), anyString(), anyString(),
            eq(CUTOFF), any(Date.class))).thenReturn(1);
        when(mapper.insertInitialSnapshot(SEASON_ID, CUTOFF)).thenReturn(1, 0);

        MonthlyRankSnapshotRow snapshot = new MonthlyRankSnapshotRow();
        snapshot.setId(901L);
        snapshot.setSeasonId(SEASON_ID);
        snapshot.setStatus("BUILDING");
        snapshot.setCutoffAt(CUTOFF);
        when(mapper.selectInitialSnapshot(SEASON_ID)).thenReturn(snapshot);
        when(mapper.selectJobCheckpoint(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(null, "2");

        MonthlyRankRow first = rank(101L, 201L, 9L, 3L,
            Date.from(Instant.parse("2026-08-20T00:00:00Z")));
        MonthlyRankRow second = rank(102L, 202L, 7L, 2L,
            Date.from(Instant.parse("2026-08-21T00:00:00Z")));
        MonthlyRankRow third = rank(103L, 203L, 5L, 1L,
            Date.from(Instant.parse("2026-08-22T00:00:00Z")));
        when(mapper.selectSourceRankingPage(SEASON_ID, CUTOFF, 0L, 1)).thenReturn(List.of(first));
        when(mapper.selectSourceRankingPage(SEASON_ID, CUTOFF, 1L, 1)).thenReturn(List.of(second));
        when(mapper.selectSourceRankingPage(SEASON_ID, CUTOFF, 2L, 1))
            .thenReturn(List.of(third), List.of(third));
        when(mapper.selectSourceRankingPage(SEASON_ID, CUTOFF, 3L, 1)).thenReturn(List.of());

        doNothing().doNothing().doThrow(new IllegalStateException("batch 3"))
            .doNothing().when(batchWriter).append(anyString(), anyString(), anyString(), anyString(),
                any(List.class), anyLong(), eq(CUTOFF));
        when(mapper.failJobRun(anyString(), anyString(), anyString(), anyString(), eq(CUTOFF), anyString()))
            .thenReturn(1);

        assertThatThrownBy(() -> service.buildSnapshot(
            SEASON_ID, "instance-a", CUTOFF, 60, 300, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("batch 3");

        first.setSnapshotId(901L);
        first.setRankNo(1);
        second.setSnapshotId(901L);
        second.setRankNo(2);
        third.setSnapshotId(901L);
        third.setRankNo(3);
        when(mapper.selectSnapshotEntryPage(901L, 0, 1)).thenReturn(List.of(first));
        when(mapper.selectSnapshotEntryPage(901L, 1, 1)).thenReturn(List.of(second));
        when(mapper.selectSnapshotEntryPage(901L, 2, 1)).thenReturn(List.of(third));
        when(mapper.selectSnapshotEntryPage(901L, 3, 1)).thenReturn(List.of());
        when(mapper.sealSnapshot(eq(901L), eq(3), eq(21L), anyString(), eq(CUTOFF))).thenReturn(1);
        when(mapper.claimSeasonReview(SEASON_ID, 5L, 901L, CUTOFF)).thenReturn(1);
        when(mapper.completeJobRun(anyString(), anyString(), anyString(), anyString(), eq(CUTOFF)))
            .thenReturn(1);

        SeasonPhaseResult retried = service.buildSnapshot(
            SEASON_ID, "instance-b", CUTOFF, 60, 300, 1);

        assertThat(retried.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(retried.seasonStatus()).isEqualTo("REVIEW");
        verify(mapper, times(2)).selectSourceRankingPage(SEASON_ID, CUTOFF, 2L, 1);
        verify(mapper).selectSourceRankingPage(SEASON_ID, CUTOFF, 0L, 1);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).sealSnapshot(eq(901L), eq(3), eq(21L), hash.capture(), eq(CUTOFF));
        assertThat(hash.getValue()).isEqualTo(hashOf(first, second, third));
    }

    @Test
    void regularSeasonWindowUsesVietnamTimeAcrossNewYear() {
        Date lastMillisecond = Date.from(Instant.parse("2026-12-31T16:59:59.999Z"));
        MonthlySeasonRow december = season("OPEN", 0L, null);
        december.setPeriodCode("2026-12");
        when(mapper.selectSeasonByPeriod("2026-12")).thenReturn(december);

        service.ensureRegularSeason(lastMillisecond, ZoneId.of("Asia/Ho_Chi_Minh"), "v1");

        verify(mapper).insertRegularSeasonIgnore("2026-12", "Asia/Ho_Chi_Minh",
            Date.from(Instant.parse("2026-11-30T17:00:00Z")),
            Date.from(Instant.parse("2026-12-31T17:00:00Z")),
            Date.from(Instant.parse("2026-12-31T17:00:00Z")), "v1");

        reset(mapper);
        Date firstMillisecond = Date.from(Instant.parse("2026-12-31T17:00:00Z"));
        MonthlySeasonRow january = season("OPEN", 0L, null);
        january.setPeriodCode("2027-01");
        when(mapper.selectSeasonByPeriod("2027-01")).thenReturn(january);

        service.ensureRegularSeason(firstMillisecond, ZoneId.of("Asia/Ho_Chi_Minh"), "v1");

        verify(mapper).insertRegularSeasonIgnore("2027-01", "Asia/Ho_Chi_Minh",
            Date.from(Instant.parse("2026-12-31T17:00:00Z")),
            Date.from(Instant.parse("2027-01-31T17:00:00Z")),
            Date.from(Instant.parse("2027-01-31T17:00:00Z")), "v1");
    }

    @Test
    void regularSeasonWindowIncludesLeapDay() {
        Date leapDay = Date.from(Instant.parse("2028-02-29T05:00:00Z"));
        MonthlySeasonRow february = season("OPEN", 0L, null);
        february.setPeriodCode("2028-02");
        when(mapper.selectSeasonByPeriod("2028-02")).thenReturn(february);

        service.ensureRegularSeason(leapDay, ZoneId.of("Asia/Ho_Chi_Minh"), "v1");

        verify(mapper).insertRegularSeasonIgnore("2028-02", "Asia/Ho_Chi_Minh",
            Date.from(Instant.parse("2028-01-31T17:00:00Z")),
            Date.from(Instant.parse("2028-02-29T17:00:00Z")),
            Date.from(Instant.parse("2028-02-29T17:00:00Z")), "v1");
    }

    @Test
    void liveRankingAssignsAbsoluteRanksAcrossPages() {
        MonthlySeasonRow open = season("OPEN", 0L, null);
        open.setPeriodCode("2026-08");
        open.setVoteCutoffAt(CUTOFF);
        when(mapper.selectSeasonByPeriod("2026-08")).thenReturn(open);
        when(mapper.countLiveRanking(SEASON_ID)).thenReturn(25L);
        MonthlyRankRow row = rank(120L, 220L, 9L, 3L, CUTOFF);
        when(mapper.selectLiveRankingPage(SEASON_ID, 10L, 10)).thenReturn(List.of(row));

        MonthlyRankingPage page = service.getRanking(null, "2026-08", 2, 10, CUTOFF);

        assertThat(page.snapshot()).isFalse();
        assertThat(page.total()).isEqualTo(25L);
        assertThat(page.entries().get(0).getRankNo()).isEqualTo(11);
    }

    @Test
    void rankingBySeasonIdReadsTheRequestedSpecialSeason() {
        MonthlySeasonRow special = season("OPEN", 0L, null);
        special.setPeriodCode("le-hoi-doc-sach-2026");
        special.setSeasonType("FESTIVAL");
        special.setVoteCutoffAt(CUTOFF);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(special);
        when(mapper.countLiveRanking(SEASON_ID)).thenReturn(0L);
        when(mapper.selectLiveRankingPage(SEASON_ID, 0L, 20)).thenReturn(List.of());

        MonthlyRankingPage page = service.getRanking(SEASON_ID, null, 1, 20, CUTOFF);

        assertThat(page.seasonId()).isEqualTo(SEASON_ID);
        assertThat(page.periodCode()).isEqualTo("le-hoi-doc-sach-2026");
        verify(mapper).selectSeasonById(SEASON_ID);
    }

    @Test
    void listsEverySeasonOpenAtTheRequestedTime() {
        MonthlySeasonRow regular = season("OPEN", 0L, null);
        MonthlySeasonRow special = season("OPEN", 0L, null);
        special.setId(72L);
        when(mapper.selectOpenSeasons(CUTOFF)).thenReturn(List.of(regular, special));

        assertThat(service.listOpenSeasons(CUTOFF)).extracting(MonthlySeasonRow::getId)
            .containsExactly(SEASON_ID, 72L);
    }

    @Test
    void pausingBeforeAJobExistsCreatesADurablePausedClaim() {
        MonthlySeasonRow closing = season("CLOSING", 5L, null);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(closing);
        when(mapper.pauseSnapshotJob(Long.toString(SEASON_ID), CUTOFF,
            "PAUSED_BY_ADMIN:9:Bảo trì đối soát dữ liệu")).thenReturn(0);
        when(mapper.insertPausedSnapshotJobIgnore(Long.toString(SEASON_ID), CUTOFF,
            "PAUSED_BY_ADMIN:9:Bảo trì đối soát dữ liệu")).thenReturn(1);

        SeasonPhaseResult result = service.pauseSnapshot(
            SEASON_ID, 9L, "Bảo trì đối soát dữ liệu", CUTOFF);

        assertThat(result.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
    }

    @Test
    void finalizedSeasonRequiresReviewSnapshotAndRecordsActor() {
        MonthlySeasonRow review = season("REVIEW", 8L, 901L);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(review);
        when(mapper.claimSeasonFinalized(SEASON_ID, 8L, 901L, 9L, CUTOFF)).thenReturn(1);

        SeasonPhaseResult result = service.finalizeSeason(SEASON_ID, 9L, CUTOFF);

        assertThat(result.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(result.seasonStatus()).isEqualTo("FINALIZED");
    }

    @Test
    void retryRepairsJobWhenSeasonReachedReviewBeforeJobCompletion() {
        MonthlySeasonRow review = season("REVIEW", 8L, 901L);
        when(mapper.selectSeasonById(SEASON_ID)).thenReturn(review);
        when(mapper.completeTerminalSnapshotJob(Long.toString(SEASON_ID), CUTOFF)).thenReturn(1);

        SeasonPhaseResult result = service.buildSnapshot(
            SEASON_ID, "instance-recovery", CUTOFF, 60, 300, 100);

        assertThat(result.outcome()).isEqualTo(SeasonPhaseResult.Outcome.COMPLETED);
        verify(mapper).completeTerminalSnapshotJob(Long.toString(SEASON_ID), CUTOFF);
    }

    @Test
    void createsSpecialSeasonWithGivenTypeAndWindow() {
        Date start = Date.from(Instant.parse("2026-09-01T00:00:00Z"));
        Date end = Date.from(Instant.parse("2026-09-08T00:00:00Z"));
        Date cutoff = Date.from(Instant.parse("2026-09-07T00:00:00Z"));
        MonthlySeasonRow inserted = season("OPEN", 0L, null);
        inserted.setPeriodCode("ky-ky-niem-2026");
        inserted.setSeasonType("ANNIVERSARY");
        when(mapper.selectSeasonByPeriod("ky-ky-niem-2026")).thenReturn(inserted);

        MonthlySeasonRow result = service.createSpecialSeason("ky-ky-niem-2026", "ANNIVERSARY",
            start, end, cutoff, ZoneId.of("Asia/Ho_Chi_Minh"), "v1");

        assertThat(result.getSeasonType()).isEqualTo("ANNIVERSARY");
        verify(mapper).insertSpecialSeasonIgnore("ky-ky-niem-2026", "ANNIVERSARY", "Asia/Ho_Chi_Minh",
            start, end, cutoff, "v1");
    }

    @Test
    void specialSeasonRejectsRegularType() {
        Date start = Date.from(Instant.parse("2026-09-01T00:00:00Z"));
        Date end = Date.from(Instant.parse("2026-09-08T00:00:00Z"));

        assertThatThrownBy(() -> service.createSpecialSeason("ky-ky-niem-2026", "REGULAR",
            start, end, end, ZoneId.of("UTC"), "v1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void specialSeasonRejectsInvertedWindow() {
        Date start = Date.from(Instant.parse("2026-09-08T00:00:00Z"));
        Date end = Date.from(Instant.parse("2026-09-01T00:00:00Z"));

        assertThatThrownBy(() -> service.createSpecialSeason("ky-ky-niem-2026", "FESTIVAL",
            start, end, start, ZoneId.of("UTC"), "v1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private MonthlySeasonRow season(String status, long version, Long snapshotId) {
        MonthlySeasonRow row = new MonthlySeasonRow();
        row.setId(SEASON_ID);
        row.setStatus(status);
        row.setVersion(version);
        row.setSnapshotId(snapshotId);
        return row;
    }

    private MonthlyRankRow rank(long bookId, long authorId, long tickets, long voters,
                                Date lastVoteAt) {
        MonthlyRankRow row = new MonthlyRankRow();
        row.setBookId(bookId);
        row.setAuthorId(authorId);
        row.setTotalTickets(tickets);
        row.setDistinctVoterCount(voters);
        row.setLastVoteAt(lastVoteAt);
        return row;
    }

    private String hashOf(MonthlyRankRow... rows) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (MonthlyRankRow row : rows) {
            String canonical = row.getRankNo() + "|" + row.getBookId() + "|"
                + row.getAuthorId() + "|" + row.getTotalTickets() + "|"
                + row.getDistinctVoterCount() + "|" + row.getLastVoteAt().getTime() + "\n";
            digest.update(canonical.getBytes(StandardCharsets.UTF_8));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
