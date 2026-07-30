package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.ReadingDailyCounterRow;
import com.java2nb.novel.service.gamification.ReadingHeartbeatReceiptRow;
import com.java2nb.novel.service.gamification.ReadingSessionRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Date;

public interface ReadingHeartbeatMapper {
    ReadingHeartbeatReceiptRow selectReceipt(@Param("sessionId") String sessionId,
                                              @Param("sequenceNo") int sequenceNo);

    int insertSessionIgnore(@Param("session") ReadingSessionRow session);

    ReadingSessionRow lockSession(@Param("sessionId") String sessionId);

    int insertDailyCounterIgnore(@Param("userId") long userId,
                                 @Param("localDate") LocalDate localDate);

    ReadingDailyCounterRow lockDailyCounter(@Param("userId") long userId,
                                            @Param("localDate") LocalDate localDate);

    int updateDailyCounter(@Param("userId") long userId,
                           @Param("localDate") LocalDate localDate,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("verifiedSeconds") int verifiedSeconds);

    int updateSession(@Param("sessionId") String sessionId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("lastSequence") int lastSequence,
                      @Param("lastHeartbeatAt") Date lastHeartbeatAt,
                      @Param("totalAcceptedSeconds") int totalAcceptedSeconds);

    int insertReceipt(@Param("receipt") ReadingHeartbeatReceiptRow receipt);
}
