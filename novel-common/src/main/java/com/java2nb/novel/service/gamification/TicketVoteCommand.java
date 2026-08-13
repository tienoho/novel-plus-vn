package com.java2nb.novel.service.gamification;

import java.time.LocalDate;
import java.util.Date;

/** Yêu cầu thắp đuốc đã được gắn danh tính máy chủ; không nhận user ID từ payload HTTP. */
public record TicketVoteCommand(
    long userId,
    long bookId,
    long seasonId,
    int amount,
    String clientRequestId,
    String sourceIpHash,
    String sourceDeviceHash,
    Date occurredAt,
    LocalDate localDate
) {

    public TicketVoteCommand {
        if (userId <= 0 || bookId <= 0 || seasonId <= 0 || amount <= 0) {
            throw new IllegalArgumentException("Yêu cầu thắp đuốc không hợp lệ");
        }
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new IllegalArgumentException("Mã yêu cầu thắp đuốc không hợp lệ");
        }
        if (sourceIpHash == null || !sourceIpHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Hash IP thắp đuốc không hợp lệ");
        }
        if (sourceDeviceHash == null || !sourceDeviceHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Hash thiết bị thắp đuốc không hợp lệ");
        }
        if (occurredAt == null || localDate == null) {
            throw new IllegalArgumentException("Thiếu thời điểm nghiệp vụ khi thắp đuốc");
        }
        occurredAt = new Date(occurredAt.getTime());
    }

    @Override
    public Date occurredAt() {
        return new Date(occurredAt.getTime());
    }
}
