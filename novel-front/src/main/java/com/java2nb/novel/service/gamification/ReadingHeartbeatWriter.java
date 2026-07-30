package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.mapper.ReadingHeartbeatMapper;
import com.java2nb.novel.service.reader.ReaderStateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ReadingHeartbeatWriter {

    private static final Pattern SESSION_ID = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    private final ReadingHeartbeatMapper mapper;
    private final ReaderStateService readerStateService;
    private final GamificationEventRecorder eventRecorder;

    @Transactional(rollbackFor = Exception.class)
    public ReadingHeartbeatResult record(ReadingHeartbeatCommand command) {
        validate(command);
        String requestHash = requestHash(command);
        ReadingHeartbeatReceiptRow existing = mapper.selectReceipt(
            command.sessionId(), command.sequence());
        if (existing != null) {
            return replay(command, requestHash, existing);
        }

        readerStateService.requireReadableChapter(
            command.userId(), command.bookId(), command.bookIndexId());
        if (command.sequence() == 0) {
            mapper.insertSessionIgnore(newSession(command));
        }
        mapper.insertDailyCounterIgnore(command.userId(), command.localDate());
        ReadingDailyCounterRow counter = mapper.lockDailyCounter(
            command.userId(), command.localDate());
        ReadingSessionRow session = mapper.lockSession(command.sessionId());
        requireRows(command, counter, session);

        existing = mapper.selectReceipt(command.sessionId(), command.sequence());
        if (existing != null) {
            return replay(command, requestHash, existing);
        }
        if (command.sequence() != session.getLastSequence() + 1) {
            throw invalidHeartbeat();
        }

        int dailyBefore = counter.getVerifiedSeconds();
        int acceptedSeconds = acceptedSeconds(command, session, dailyBefore);
        int dailyAfter = Math.addExact(dailyBefore, acceptedSeconds);
        int totalAccepted = Math.addExact(session.getTotalAcceptedSeconds(), acceptedSeconds);
        if (acceptedSeconds > 0 && mapper.updateDailyCounter(command.userId(), command.localDate(),
            counter.getVersion(), dailyAfter) != 1) {
            throw new IllegalStateException("Bộ đếm thời gian đọc đã được cập nhật đồng thời");
        }
        if (mapper.updateSession(command.sessionId(), session.getVersion(), command.sequence(),
            command.heartbeatAt(), totalAccepted) != 1) {
            throw new IllegalStateException("Phiên đọc đã được cập nhật đồng thời");
        }

        int firstMinute = dailyBefore / 60 + 1;
        int eventCount = dailyAfter / 60 - dailyBefore / 60;
        ReadingHeartbeatReceiptRow receipt = receipt(command, requestHash, acceptedSeconds,
            dailyAfter, firstMinute, eventCount);
        if (mapper.insertReceipt(receipt) != 1) {
            throw new IllegalStateException("Không thể ghi biên nhận heartbeat đọc");
        }
        List<String> sourceKeys = recordMinuteEvents(command, firstMinute, eventCount);
        return result(command, acceptedSeconds, dailyAfter, sourceKeys, false);
    }

    static String requestHash(ReadingHeartbeatCommand command) {
        return ContentHashUtil.sha256Hex(command.userId() + "|" + command.sessionId() + "|"
            + command.bookId() + "|" + command.bookIndexId() + "|" + command.sequence() + "|"
            + command.activeSeconds() + "|" + command.policyVersion());
    }

    private void validate(ReadingHeartbeatCommand command) {
        if (command == null || command.userId() <= 0 || command.bookId() <= 0
            || command.bookIndexId() <= 0 || command.sessionId() == null
            || !SESSION_ID.matcher(command.sessionId()).matches() || command.sequence() < 0
            || command.activeSeconds() < 0 || command.heartbeatAt() == null
            || command.localDate() == null || command.zoneId() == null
            || command.intervalSeconds() <= 0 || command.maxMinutesPerDay() <= 0
            || command.policyVersion() == null || command.policyVersion().isBlank()
            || command.policyVersion().length() > 32
            || command.activeSeconds() > Math.multiplyExact(command.intervalSeconds(), 2)
            || (command.sequence() == 0 && command.activeSeconds() != 0)
            || !command.heartbeatAt().toInstant().atZone(command.zoneId()).toLocalDate()
                .equals(command.localDate())) {
            throw invalidHeartbeat();
        }
    }

    private ReadingSessionRow newSession(ReadingHeartbeatCommand command) {
        ReadingSessionRow row = new ReadingSessionRow();
        row.setSessionId(command.sessionId());
        row.setUserId(command.userId());
        row.setBookId(command.bookId());
        row.setBookIndexId(command.bookIndexId());
        row.setLastSequence(-1);
        row.setLastHeartbeatAt(command.heartbeatAt());
        row.setTotalAcceptedSeconds(0);
        row.setStatus("ACTIVE");
        row.setVersion(0L);
        return row;
    }

    private void requireRows(ReadingHeartbeatCommand command, ReadingDailyCounterRow counter,
                             ReadingSessionRow session) {
        if (counter == null || counter.getVerifiedSeconds() == null || counter.getVersion() == null) {
            throw new IllegalStateException("Không thể khóa bộ đếm thời gian đọc trong ngày");
        }
        if (session == null || !Objects.equals(session.getUserId(), command.userId())
            || !Objects.equals(session.getBookId(), command.bookId())
            || !Objects.equals(session.getBookIndexId(), command.bookIndexId())
            || !"ACTIVE".equals(session.getStatus()) || session.getLastSequence() == null
            || session.getLastHeartbeatAt() == null || session.getTotalAcceptedSeconds() == null
            || session.getVersion() == null) {
            throw invalidHeartbeat();
        }
    }

    private int acceptedSeconds(ReadingHeartbeatCommand command, ReadingSessionRow session,
                                int dailyBefore) {
        if (command.sequence() == 0 || command.activeSeconds() == 0) {
            return 0;
        }
        long elapsed = ChronoUnit.SECONDS.between(
            session.getLastHeartbeatAt().toInstant(), command.heartbeatAt().toInstant());
        if (elapsed < 0) {
            throw invalidHeartbeat();
        }
        if (elapsed < Math.max(1, command.intervalSeconds() / 2)
            || elapsed > Math.multiplyExact(command.intervalSeconds(), 2)) {
            return 0;
        }
        int maxDailySeconds = Math.multiplyExact(command.maxMinutesPerDay(), 60);
        int remaining = Math.max(0, maxDailySeconds - dailyBefore);
        long bounded = Math.min(command.activeSeconds(), elapsed);
        bounded = Math.min(bounded, Math.multiplyExact(command.intervalSeconds(), 2));
        return (int) Math.min(bounded, remaining);
    }

    private ReadingHeartbeatReceiptRow receipt(ReadingHeartbeatCommand command, String requestHash,
                                                int acceptedSeconds, int dailyAfter,
                                                int firstMinute, int eventCount) {
        ReadingHeartbeatReceiptRow row = new ReadingHeartbeatReceiptRow();
        row.setSessionId(command.sessionId());
        row.setUserId(command.userId());
        row.setBookId(command.bookId());
        row.setBookIndexId(command.bookIndexId());
        row.setSequenceNo(command.sequence());
        row.setActiveSeconds(command.activeSeconds());
        row.setAcceptedSeconds(acceptedSeconds);
        row.setDailySecondsAfter(dailyAfter);
        row.setFirstMinuteBucket(eventCount == 0 ? null : firstMinute);
        row.setEventCount(eventCount);
        row.setLocalDate(command.localDate());
        row.setRequestHash(requestHash);
        row.setHeartbeatAt(command.heartbeatAt());
        row.setPolicyVersion(command.policyVersion());
        return row;
    }

    private List<String> recordMinuteEvents(ReadingHeartbeatCommand command, int firstMinute,
                                            int eventCount) {
        List<String> sourceKeys = new ArrayList<>(eventCount);
        for (int offset = 0; offset < eventCount; offset++) {
            int minuteBucket = firstMinute + offset;
            String sourceKey = sourceKey(command.userId(), command.sessionId(),
                command.localDate().toString(), minuteBucket);
            eventRecorder.ingest(GamificationEventInputFactory.create(
                "READING_MINUTE_VERIFIED", sourceKey, command.userId(), command.bookId(),
                command.heartbeatAt(), "{\"minuteBucket\":" + minuteBucket + '}',
                command.zoneId(), command.policyVersion()));
            sourceKeys.add(sourceKey);
        }
        return sourceKeys;
    }

    private ReadingHeartbeatResult replay(ReadingHeartbeatCommand command, String requestHash,
                                          ReadingHeartbeatReceiptRow receipt) {
        if (!Objects.equals(receipt.getUserId(), command.userId())
            || !Objects.equals(receipt.getBookId(), command.bookId())
            || !Objects.equals(receipt.getBookIndexId(), command.bookIndexId())
            || !Objects.equals(receipt.getActiveSeconds(), command.activeSeconds())
            || !Objects.equals(receipt.getRequestHash(), requestHash)) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_IDEMPOTENCY_CONFLICT);
        }
        int firstMinute = receipt.getFirstMinuteBucket() == null ? 0 : receipt.getFirstMinuteBucket();
        List<String> sourceKeys = new ArrayList<>(receipt.getEventCount());
        for (int offset = 0; offset < receipt.getEventCount(); offset++) {
            sourceKeys.add(sourceKey(command.userId(), command.sessionId(),
                receipt.getLocalDate().toString(), firstMinute + offset));
        }
        return result(command, receipt.getAcceptedSeconds(), receipt.getDailySecondsAfter(),
            sourceKeys, true);
    }

    private ReadingHeartbeatResult result(ReadingHeartbeatCommand command, int acceptedSeconds,
                                          int dailySecondsAfter, List<String> sourceKeys,
                                          boolean replayed) {
        int maxDailySeconds = Math.multiplyExact(command.maxMinutesPerDay(), 60);
        return new ReadingHeartbeatResult(command.sessionId(), acceptedSeconds,
            dailySecondsAfter / 60, dailySecondsAfter >= maxDailySeconds,
            command.sequence() + 1, replayed, sourceKeys);
    }

    private String sourceKey(long userId, String sessionId, String localDate, int minuteBucket) {
        return "READ:" + userId + ':' + sessionId + ':' + localDate + ':' + minuteBucket;
    }

    private BusinessException invalidHeartbeat() {
        return new BusinessException(ResponseStatus.GAMIFICATION_READING_HEARTBEAT_INVALID);
    }
}
