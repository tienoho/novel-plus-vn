package com.java2nb.novel.service.gamification;

/**
 * Tập ngưỡng bất biến được chụp từ {@code GamificationConfigProvider} một lần cho mỗi thao tác.
 * Service nhận bản ghi này thay vì đọc lại provider giữa transaction, nhờ đó toàn bộ lần bỏ phiếu
 * dùng cùng runtime revision và policy version kể cả khi admin kích hoạt revision mới đồng thời.
 */
public record TicketPolicy(
    String policyVersion,
    int lotValidityDays,
    int maxTicketsPerRequest,
    int maxVotesPerDay,
    int maxTicketsPerDay,
    int maxTicketsPerBookPerSeason,
    int maxLotsPerSpend,
    boolean allowCrawledBooks
) {

    public TicketPolicy {
        if (policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("Thiếu phiên bản chính sách gamification");
        }
        if (lotValidityDays <= 0) {
            throw new IllegalArgumentException("Hạn dùng Ngọn Đuốc phải lớn hơn 0 ngày");
        }
        if (maxTicketsPerRequest <= 0 || maxVotesPerDay <= 0 || maxLotsPerSpend <= 0) {
            throw new IllegalArgumentException("Giới hạn bỏ phiếu phải lớn hơn 0");
        }
        // Trần ngày thấp hơn trần một lần gửi sẽ khiến không lần thắp đuốc nào hợp lệ.
        if (maxTicketsPerDay < maxTicketsPerRequest
            || maxTicketsPerBookPerSeason < maxTicketsPerRequest) {
            throw new IllegalArgumentException("Trần ngày và trần theo tác phẩm phải lớn hơn hoặc bằng trần mỗi lần gửi");
        }
    }
}
