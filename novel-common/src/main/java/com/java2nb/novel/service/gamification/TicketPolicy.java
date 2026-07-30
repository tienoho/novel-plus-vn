package com.java2nb.novel.service.gamification;

/**
 * Ngưỡng chính sách được truyền vào service thay vì đọc từ cấu hình.
 *
 * <p>{@code novel-common} cố ý không có lớp {@code @ConfigurationProperties} nào cho gamification.
 * Module dùng chung được nạp bởi cả ba ứng dụng với profile khác nhau, nên gắn nó vào một cây cấu
 * hình cụ thể sẽ khiến hành vi nghiệp vụ phụ thuộc vào ứng dụng nào đang gọi. Bên gọi dựng bản ghi
 * này từ {@code GamificationProperties} rồi truyền xuống.
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
