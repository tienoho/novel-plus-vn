package com.java2nb.novel.service.gamification;

import java.time.LocalDate;
import java.util.Date;

/**
 * Sự kiện nguồn được ghi trong transaction nghiệp vụ gốc.
 *
 * <p>Việc ghi chỉ tạo một hàng {@code PENDING}; worker ở {@code novel-front} áp dụng quest/EXP
 * trong transaction riêng. {@code payloadJson} không được chứa PII, token hoặc dữ liệu thanh
 * toán. Hash được tính từ payload chuẩn hoá ở bên gọi để phát hiện tái sử dụng {@code sourceKey}
 * với nội dung khác.
 */
public record GamificationEventInput(
    String eventType,
    String sourceKey,
    long userId,
    Long bookId,
    Date occurredAt,
    LocalDate localDate,
    String payloadHash,
    String payloadJson,
    String policyVersion,
    long runtimeConfigRevision
) {

    public GamificationEventInput {
        if (eventType == null || eventType.isBlank() || eventType.length() > 48) {
            throw new IllegalArgumentException("Loại sự kiện gamification không hợp lệ");
        }
        if (sourceKey == null || sourceKey.isBlank() || sourceKey.length() > 160) {
            throw new IllegalArgumentException("Khóa nguồn sự kiện gamification không hợp lệ");
        }
        if (userId <= 0 || (bookId != null && bookId <= 0)) {
            throw new IllegalArgumentException("Chủ thể sự kiện gamification không hợp lệ");
        }
        if (occurredAt == null || localDate == null) {
            throw new IllegalArgumentException("Thiếu thời điểm nghiệp vụ của sự kiện gamification");
        }
        occurredAt = new Date(occurredAt.getTime());
        if (payloadHash == null || !payloadHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Hash payload gamification phải là SHA-256 chữ thường");
        }
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Phiên bản chính sách gamification không hợp lệ");
        }
        if (runtimeConfigRevision <= 0) {
            throw new IllegalArgumentException("Revision cấu hình gamification không hợp lệ");
        }
    }

    @Override
    public Date occurredAt() {
        return new Date(occurredAt.getTime());
    }
}
