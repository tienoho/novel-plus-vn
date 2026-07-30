package com.java2nb.novel.service.gamification;

import java.time.LocalDate;
import java.util.Date;

/** Yêu cầu thắp đuốc đã được gắn danh tính máy chủ; không nhận user ID từ payload HTTP. */
public record TicketVoteCommand(
    long userId,
    long bookId,
    int count,
    String clientRequestId,
    String sourceIpHash,
    Date occurredAt,
    LocalDate localDate
) {

    public TicketVoteCommand {
        if (userId <= 0 || bookId <= 0 || count <= 0) {
            throw new IllegalArgumentException("Yêu cầu thắp đuốc không hợp lệ");
        }
        if (clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64) {
            throw new IllegalArgumentException("Mã yêu cầu thắp đuốc không hợp lệ");
        }
        if (sourceIpHash == null || !sourceIpHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Hash IP thắp đuốc không hợp lệ");
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
