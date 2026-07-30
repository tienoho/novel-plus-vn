package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class GamificationEventFailureWriter {
    private final GamificationProgressMapper mapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(long eventId, Date failedAt, Throwable failure, int maxAttempt) {
        GamificationEventRow event = mapper.selectEventById(eventId);
        if (event == null || !"PENDING".equals(event.getStatus())) {
            return;
        }
        String message = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
        if (message.length() > 500) {
            message = message.substring(0, 500);
        }
        mapper.recordEventFailure(eventId, event.getVersion(), failedAt, message, maxAttempt);
    }
}
