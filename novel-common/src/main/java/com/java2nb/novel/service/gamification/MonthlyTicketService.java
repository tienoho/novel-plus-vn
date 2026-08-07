package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.util.List;

/**
 * Nghiệp vụ Ngọn Đuốc: cấp, tra cứu và về sau là tiêu theo FIFO.
 *
 * <p>Ranh giới transaction nằm ở đây. Controller của {@code novel-front} và {@code novel-admin}
 * không được tự ghép nhiều mapper để cấp hay tiêu phiếu.
 *
 * <p>Ngọn Đuốc không phải tài sản tài chính: không đổi ngược thành Xu, không chuyển nhượng và
 * không có trạng thái nợ. Vì vậy ở đây không có tham số nào tương đương cờ {@code allowReaderDebt}
 * của sổ cái Xu, và ràng buộc CHECK trên bảng tài khoản là hàng rào cuối.
 */
public interface MonthlyTicketService {

    /**
     * Cấp một lô Ngọn Đuốc. Gọi lại với cùng khoá idempotency và cùng nội dung sẽ trả về
     * {@link TicketPostResult#ALREADY_POSTED} mà không tạo lô thứ hai; cùng khoá với nội dung khác
     * bị từ chối bằng {@link IllegalStateException}.
     */
    TicketPostResult grant(TicketGrantCommand command);

    /** Trả về tài khoản, tạo lười nếu người dùng chưa từng có Ngọn Đuốc. */
    TicketAccountRow getOrCreateAccount(long userId);

    /** Các lô còn hiệu lực, sắp xếp theo thứ tự sẽ bị tiêu: lô sắp tắt trước. */
    List<TicketLotRow> listActiveLots(long userId, Date now, int limit);

    TicketHistoryPage listHistory(long userId, int page, int pageSize);

    /**
     * Đóng toàn bộ lot đã hết hạn của một người dùng tại mốc cutoff, ghi một bút toán EXPIRE và
     * cập nhật projection trong cùng transaction. Scheduler phải gọi từng user riêng để giữ thứ
     * tự khóa account trước, lot sau.
     */
    TicketExpiryResult expireDueLots(long userId, Date cutoff, String scopeKey, String policyVersion);

    /** Tiêu lot theo FIFO và ghi vote/rank projection trong một transaction. */
    TicketVoteResult castVote(TicketVoteCommand command, TicketPolicy policy);

    TicketBookSummary getBookSummary(long userId, long bookId, long seasonId, Date now,
                                     TicketPolicy policy);

    /**
     * Danh sách vote gần nhất cho bảng chạy công khai. Chỉ trả về vote của người dùng đã bật
     * tường minh (mặc định của cột là ẩn), và loại bỏ mọi nickname vướng bộ lọc từ nhạy cảm thay
     * vì hiển thị — ưu tiên ẩn một dòng còn hơn để lọt nội dung không phù hợp.
     */
    List<TickerEntryRow> listTicker(int limit);
}
