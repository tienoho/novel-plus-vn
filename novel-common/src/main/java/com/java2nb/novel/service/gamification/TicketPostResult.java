package com.java2nb.novel.service.gamification;

/**
 * Kết quả ghi sổ Ngọn Đuốc. Cùng ngữ nghĩa với {@code WalletPostResult} của sổ cái Xu: một lần
 * gửi lại với cùng khoá idempotency và cùng nội dung không tạo bút toán thứ hai mà trả về
 * {@link #ALREADY_POSTED}, còn cùng khoá với nội dung khác thì bị từ chối bằng ngoại lệ.
 */
public enum TicketPostResult {
    POSTED,
    ALREADY_POSTED
}
