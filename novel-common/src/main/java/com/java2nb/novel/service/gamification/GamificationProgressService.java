package com.java2nb.novel.service.gamification;

import java.time.ZoneId;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

public interface GamificationProgressService {

    GamificationProfileRow getProfile(long userId, String ruleVersion);

    GamificationProfileSnapshot getProfileSnapshot(long userId, String ruleVersion);

    RealmUpdateResult updateRealm(long userId, String realmCode, long expectedVersion,
                                  Date changedAt, ZoneId zoneId, int cooldownHours,
                                  String ruleVersion);

    int applyEvent(GamificationEventRow event);

    List<QuestProgressRow> listQuests(long userId, LocalDate localDate, Date observedAt);

    QuestClaimResult claimQuest(QuestClaimCommand command);

    CheckInResult checkIn(long userId, LocalDate localDate, Date checkedAt, ZoneId zoneId,
                          String ruleVersion, String policyVersion);

    /**
     * Bật/tắt hiển thị trên bảng chạy công khai. Mặc định cột là ẩn (opt-out); người dùng phải
     * tự bật. Ghi audit mọi lần đổi, dùng chung bảng với đổi cảnh giới.
     */
    GamificationProfileRow updateTickerOptOut(long userId, boolean optOut, long expectedVersion,
                                              String ruleVersion);

    /**
     * Kiểm duyệt admin: ẩn/hiện một người dùng khỏi bảng chạy công khai (ví dụ nickname vi phạm
     * vượt qua bộ lọc từ nhạy cảm tự động), bỏ qua {@code expectedVersion} phía client vì admin
     * khoá và đọc version mới nhất ngay trong giao dịch. Ghi audit với operatorType 'ADMIN'.
     */
    GamificationProfileRow adminSetTickerOptOut(long userId, boolean optOut, long operatorId,
                                                String reason, String ruleVersion);
}
