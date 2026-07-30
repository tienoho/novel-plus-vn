package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.gamification.EventProcessResult;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class GamificationEventProcessor {
    private final GamificationProgressMapper mapper;
    private final GamificationProgressService progressService;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public EventProcessResult process(long eventId, Date processedAt, int maxAttempt) {
        GamificationEventRow event = mapper.selectEventById(eventId);
        if (event == null || !"PENDING".equals(event.getStatus())
            || mapper.claimEvent(eventId, event.getVersion(), processedAt, maxAttempt) != 1) {
            return EventProcessResult.NOT_OWNER;
        }
        int matched = progressService.applyEvent(event);
        String status = matched == 0 ? "SKIPPED" : "PROCESSED";
        if (mapper.completeEvent(eventId, event.getVersion() + 1, status, processedAt) != 1) {
            throw new IllegalStateException("Mất quyền sở hữu event gamification khi hoàn tất");
        }
        return matched == 0 ? EventProcessResult.SKIPPED : EventProcessResult.PROCESSED;
    }
}
