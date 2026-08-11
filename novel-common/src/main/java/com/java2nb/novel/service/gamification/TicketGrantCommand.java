package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.util.Set;

/**
 * Yêu cầu cấp Ngọn Đuốc.
 *
 * <p>{@code idempotencyKey} luôn do máy chủ sinh từ danh tính đã xác thực và tham số nghiệp vụ,
 * không bao giờ nhận từ máy khách. Ví dụ {@code CHECKIN:<userId>:<localDate>} hoặc
 * {@code ADMIN_GRANT:<batchId>:<userId>}.
 *
 * <p>{@code operatorType} là {@code ADMIN} thì {@code operatorId} và {@code reason} bắt buộc phải
 * có; ràng buộc CHECK trên bảng sổ cái từ chối bản ghi thiếu hai trường này.
 */
public record TicketGrantCommand(
    long userId,
    long amount,
    String sourceType,
    String sourceRef,
    String idempotencyKey,
    Date effectiveAt,
    Date expireAt,
    String operatorType,
    Long operatorId,
    String reason,
    String policyVersion,
    long runtimeConfigRevision
) {

    private static final Set<String> SOURCE_TYPES = Set.of(
        "ADMIN_GRANT", "PROMOTION", "QUEST", "CHECK_IN", "LEVEL_UP", "COMPENSATION");
    private static final Set<String> OPERATOR_TYPES = Set.of("SYSTEM", "ADMIN");

    public TicketGrantCommand {
        if (userId <= 0) {
            throw new IllegalArgumentException("ID người nhận Ngọn Đuốc không hợp lệ");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Số Ngọn Đuốc cấp phải lớn hơn 0");
        }
        if (sourceType == null || !SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException("Thiếu nguồn cấp Ngọn Đuốc");
        }
        if (sourceRef == null || sourceRef.isBlank() || sourceRef.length() > 128) {
            throw new IllegalArgumentException("Thiếu mã tham chiếu nguồn cấp");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Thiếu khóa idempotency khi cấp Ngọn Đuốc");
        }
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Khóa idempotency vượt quá 128 ký tự");
        }
        if (effectiveAt == null || expireAt == null || !expireAt.after(effectiveAt)) {
            throw new IllegalArgumentException("Khoảng hiệu lực của lô Ngọn Đuốc không hợp lệ");
        }
        effectiveAt = new Date(effectiveAt.getTime());
        expireAt = new Date(expireAt.getTime());
        if (operatorType == null || !OPERATOR_TYPES.contains(operatorType)) {
            throw new IllegalArgumentException("Thiếu loại người thao tác");
        }
        if ("ADMIN".equals(operatorType)
            && (operatorId == null || operatorId <= 0 || reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("Thao tác của quản trị viên phải có người thực hiện và lý do");
        }
        if (reason != null && reason.length() > 255) {
            throw new IllegalArgumentException("Lý do cấp Ngọn Đuốc vượt quá 255 ký tự");
        }
        if (policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Thiếu phiên bản chính sách gamification");
        }
        if (runtimeConfigRevision <= 0) {
            throw new IllegalArgumentException("Revision cấu hình gamification không hợp lệ");
        }
    }

    @Override
    public Date effectiveAt() {
        return new Date(effectiveAt.getTime());
    }

    @Override
    public Date expireAt() {
        return new Date(expireAt.getTime());
    }
}
