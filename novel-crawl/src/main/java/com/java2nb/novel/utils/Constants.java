package com.java2nb.novel.utils;

/**
 * @author Administrator
 */
public class Constants {

    /**
     * Tiền tố lưu ảnh cục bộ
     */
    public static final String LOCAL_PIC_PREFIX = "/localPic/";

    /**
     * Giá trị lượt xem mặc định
     */
    public static final Long VISIT_COUNT_DEFAULT = 100L;

    /**
     * Độ dài nội dung được xem là không hợp lệ trong yêu cầu HTTP thu thập truyện
     */
    public static final int INVALID_HTML_LENGTH = 1500;

    /**
     * Số lần thử lại khi yêu cầu HTTP thu thập truyện thất bại
     */
    public static final Integer HTTP_FAIL_RETRY_COUNT = 3;

    /**
     * Bảng mã mặc định của trình thu thập
     */
    public static final String CRAWL_DEFAULT_CHARSET = "UTF-8";
}
