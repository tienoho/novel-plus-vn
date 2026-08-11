package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.gamification.GamificationEventInput;
import com.java2nb.novel.service.gamification.GamificationEventPostResult;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GamificationEventRecorderImpl implements GamificationEventRecorder {

    private final GamificationProgressMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GamificationEventPostResult ingest(GamificationEventInput event) {
        Objects.requireNonNull(event, "Thiếu sự kiện gamification");
        if (mapper.insertEventIgnore(event) == 1) {
            return GamificationEventPostResult.POSTED;
        }

        GamificationEventRow existing = mapper.selectEventBySourceKey(event.sourceKey());
        if (existing == null) {
            throw new IllegalStateException("Không đọc được sự kiện gamification sau xung đột idempotency");
        }
        validateExisting(existing, event);
        return GamificationEventPostResult.ALREADY_POSTED;
    }

    private void validateExisting(GamificationEventRow existing, GamificationEventInput requested) {
        if (!Objects.equals(existing.getEventType(), requested.eventType())
            || !Objects.equals(existing.getUserId(), requested.userId())
            || !Objects.equals(existing.getBookId(), requested.bookId())
            || !Objects.equals(existing.getLocalDate(), requested.localDate())
            || !Objects.equals(existing.getPayloadHash(), requested.payloadHash())
            || !Objects.equals(existing.getPolicyVersion(), requested.policyVersion())
            || !Objects.equals(existing.getRuntimeConfigRevision(), requested.runtimeConfigRevision())) {
            throw new IllegalStateException("Khóa nguồn sự kiện gamification đã được dùng cho nội dung khác");
        }
    }
}
