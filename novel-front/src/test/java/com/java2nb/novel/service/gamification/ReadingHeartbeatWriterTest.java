package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.ReadingHeartbeatMapper;
import com.java2nb.novel.service.reader.ReaderStateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingHeartbeatWriterTest {

    private static final long USER_ID = 11L;
    private static final long BOOK_ID = 21L;
    private static final long CHAPTER_ID = 31L;
    private static final String SESSION_ID = "0123456789abcdef0123456789abcdef";
    private static final ZoneId ZONE_ID = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate LOCAL_DATE = LocalDate.of(2026, 7, 30);
    private static final Date STARTED_AT = Date.from(Instant.parse("2026-07-30T03:00:00Z"));

    private ReadingHeartbeatMapper mapper;
    private ReaderStateService readerStateService;
    private GamificationEventRecorder eventRecorder;
    private ReadingHeartbeatWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(ReadingHeartbeatMapper.class);
        readerStateService = mock(ReaderStateService.class);
        eventRecorder = mock(GamificationEventRecorder.class);
        writer = new ReadingHeartbeatWriter(mapper, readerStateService, eventRecorder);
    }

    @Test
    void firstHeartbeatOpensSessionWithoutCreditingTime() {
        ReadingSessionRow session = session(-1, STARTED_AT, 0, 0L);
        ReadingDailyCounterRow counter = counter(0, 0L);
        when(mapper.insertSessionIgnore(any())).thenReturn(1);
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter);
        when(mapper.lockSession(SESSION_ID)).thenReturn(session);
        when(mapper.updateSession(SESSION_ID, 0L, 0, STARTED_AT, 0)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(0, 0, STARTED_AT));

        assertThat(result.acceptedSeconds()).isZero();
        assertThat(result.verifiedMinutesToday()).isZero();
        assertThat(result.nextSequence()).isEqualTo(1);
        assertThat(result.replayed()).isFalse();
        assertThat(result.eventSourceKeys()).isEmpty();
        verify(readerStateService).requireReadableChapter(USER_ID, BOOK_ID, CHAPTER_ID);
        verify(mapper, never()).updateDailyCounter(USER_ID, LOCAL_DATE, 0L, 0);
        verify(eventRecorder, never()).ingest(any());
    }

    @Test
    void creditsElapsedActiveMinuteAndRecordsOneEvent() {
        Date heartbeatAt = Date.from(STARTED_AT.toInstant().plusSeconds(60));
        ReadingSessionRow session = session(0, STARTED_AT, 0, 1L);
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(0, 0L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session);
        when(mapper.updateDailyCounter(USER_ID, LOCAL_DATE, 0L, 60)).thenReturn(1);
        when(mapper.updateSession(SESSION_ID, 1L, 1, heartbeatAt, 60)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(1, 60, heartbeatAt));

        assertThat(result.acceptedSeconds()).isEqualTo(60);
        assertThat(result.verifiedMinutesToday()).isEqualTo(1);
        assertThat(result.eventSourceKeys())
            .containsExactly("READ:11:" + SESSION_ID + ":2026-07-30:1");
        ArgumentCaptor<GamificationEventInput> event =
            ArgumentCaptor.forClass(GamificationEventInput.class);
        verify(eventRecorder).ingest(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("READING_MINUTE_VERIFIED");
        assertThat(event.getValue().localDate()).isEqualTo(LOCAL_DATE);
        assertThat(event.getValue().payloadJson()).isEqualTo("{\"minuteBucket\":1}");
    }

    @Test
    void replaysSameSequenceAndPayloadFromImmutableReceipt() {
        ReadingHeartbeatCommand command = command(1, 60,
            Date.from(STARTED_AT.toInstant().plusSeconds(60)));
        ReadingHeartbeatReceiptRow receipt = receipt(command, 60, 60, 1, 1);
        when(mapper.selectReceipt(SESSION_ID, 1)).thenReturn(receipt);

        ReadingHeartbeatResult result = writer.record(command);

        assertThat(result.replayed()).isTrue();
        assertThat(result.acceptedSeconds()).isEqualTo(60);
        assertThat(result.eventSourceKeys())
            .containsExactly("READ:11:" + SESSION_ID + ":2026-07-30:1");
        verify(readerStateService, never()).requireReadableChapter(USER_ID, BOOK_ID, CHAPTER_ID);
        verify(mapper, never()).lockSession(SESSION_ID);
    }

    @Test
    void rejectsReusedSequenceWithDifferentPayload() {
        ReadingHeartbeatCommand original = command(1, 60,
            Date.from(STARTED_AT.toInstant().plusSeconds(60)));
        when(mapper.selectReceipt(SESSION_ID, 1))
            .thenReturn(receipt(original, 60, 60, 1, 1));

        assertThatThrownBy(() -> writer.record(command(1, 30,
            Date.from(STARTED_AT.toInstant().plusSeconds(90)))))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).lockSession(SESSION_ID);
    }

    @Test
    void doesNotCreditHeartbeatThatArrivesTooQuickly() {
        Date heartbeatAt = Date.from(STARTED_AT.toInstant().plusSeconds(20));
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(0, 0L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session(0, STARTED_AT, 0, 1L));
        when(mapper.updateSession(SESSION_ID, 1L, 1, heartbeatAt, 0)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(1, 60, heartbeatAt));

        assertThat(result.acceptedSeconds()).isZero();
        verify(mapper, never()).updateDailyCounter(USER_ID, LOCAL_DATE, 0L, 0);
        verify(eventRecorder, never()).ingest(any());
    }

    @Test
    void creditsAtMaximumPermittedHeartbeatGap() {
        Date heartbeatAt = Date.from(STARTED_AT.toInstant().plusSeconds(120));
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(0, 0L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session(0, STARTED_AT, 0, 1L));
        when(mapper.updateDailyCounter(USER_ID, LOCAL_DATE, 0L, 120)).thenReturn(1);
        when(mapper.updateSession(SESSION_ID, 1L, 1, heartbeatAt, 120)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(1, 120, heartbeatAt));

        assertThat(result.acceptedSeconds()).isEqualTo(120);
        assertThat(result.eventSourceKeys()).containsExactly(
            "READ:11:" + SESSION_ID + ":2026-07-30:1",
            "READ:11:" + SESSION_ID + ":2026-07-30:2");
    }

    @Test
    void doesNotCreditAfterLongHeartbeatGap() {
        Date heartbeatAt = Date.from(STARTED_AT.toInstant().plusSeconds(121));
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(120, 2L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session(3, STARTED_AT, 120, 4L));
        when(mapper.updateSession(SESSION_ID, 4L, 4, heartbeatAt, 120)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(4, 60, heartbeatAt));

        assertThat(result.acceptedSeconds()).isZero();
        assertThat(result.verifiedMinutesToday()).isEqualTo(2);
        verify(eventRecorder, never()).ingest(any());
    }

    @Test
    void capsAcceptedTimeAtConfiguredDailyMaximum() {
        Date heartbeatAt = Date.from(STARTED_AT.toInstant().plusSeconds(60));
        int before = 179 * 60 + 30;
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(before, 8L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session(0, STARTED_AT, 0, 1L));
        when(mapper.updateDailyCounter(USER_ID, LOCAL_DATE, 8L, 180 * 60)).thenReturn(1);
        when(mapper.updateSession(SESSION_ID, 1L, 1, heartbeatAt, 30)).thenReturn(1);
        when(mapper.insertReceipt(any())).thenReturn(1);

        ReadingHeartbeatResult result = writer.record(command(1, 60, heartbeatAt));

        assertThat(result.acceptedSeconds()).isEqualTo(30);
        assertThat(result.verifiedMinutesToday()).isEqualTo(180);
        assertThat(result.capReached()).isTrue();
        assertThat(result.eventSourceKeys())
            .containsExactly("READ:11:" + SESSION_ID + ":2026-07-30:180");
    }

    @Test
    void rejectsOutOfOrderSequenceBeforeChangingCounters() {
        when(mapper.lockDailyCounter(USER_ID, LOCAL_DATE)).thenReturn(counter(0, 0L));
        when(mapper.lockSession(SESSION_ID)).thenReturn(session(0, STARTED_AT, 0, 1L));

        assertThatThrownBy(() -> writer.record(command(2, 60,
            Date.from(STARTED_AT.toInstant().plusSeconds(60)))))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).updateSession(any(), any(Long.class),
            org.mockito.ArgumentMatchers.anyInt(), any(), org.mockito.ArgumentMatchers.anyInt());
        verify(eventRecorder, never()).ingest(any());
    }

    private ReadingHeartbeatCommand command(int sequence, int activeSeconds, Date heartbeatAt) {
        return new ReadingHeartbeatCommand(USER_ID, SESSION_ID, BOOK_ID, CHAPTER_ID, sequence,
            activeSeconds, heartbeatAt, LOCAL_DATE, ZONE_ID, 60, 180, "v1");
    }

    private ReadingSessionRow session(int sequence, Date heartbeatAt, int totalSeconds, long version) {
        ReadingSessionRow row = new ReadingSessionRow();
        row.setSessionId(SESSION_ID);
        row.setUserId(USER_ID);
        row.setBookId(BOOK_ID);
        row.setBookIndexId(CHAPTER_ID);
        row.setLastSequence(sequence);
        row.setLastHeartbeatAt(heartbeatAt);
        row.setTotalAcceptedSeconds(totalSeconds);
        row.setStatus("ACTIVE");
        row.setVersion(version);
        return row;
    }

    private ReadingDailyCounterRow counter(int seconds, long version) {
        ReadingDailyCounterRow row = new ReadingDailyCounterRow();
        row.setUserId(USER_ID);
        row.setLocalDate(LOCAL_DATE);
        row.setVerifiedSeconds(seconds);
        row.setVersion(version);
        return row;
    }

    private ReadingHeartbeatReceiptRow receipt(ReadingHeartbeatCommand command, int acceptedSeconds,
                                                int dailySecondsAfter, int firstMinute, int eventCount) {
        ReadingHeartbeatReceiptRow row = new ReadingHeartbeatReceiptRow();
        row.setSessionId(SESSION_ID);
        row.setUserId(USER_ID);
        row.setBookId(BOOK_ID);
        row.setBookIndexId(CHAPTER_ID);
        row.setSequenceNo(command.sequence());
        row.setActiveSeconds(command.activeSeconds());
        row.setAcceptedSeconds(acceptedSeconds);
        row.setDailySecondsAfter(dailySecondsAfter);
        row.setFirstMinuteBucket(firstMinute);
        row.setEventCount(eventCount);
        row.setLocalDate(LOCAL_DATE);
        row.setRequestHash(ReadingHeartbeatWriter.requestHash(command));
        return row;
    }
}
