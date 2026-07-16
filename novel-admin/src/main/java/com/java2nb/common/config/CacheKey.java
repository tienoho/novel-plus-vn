package com.java2nb.common.config;

/**
 * @author 11797
 */
public interface CacheKey {

    /**
     * Cấu hình tác phẩm trang chủ
     */
    String INDEX_BOOK_SETTINGS_KEY = "indexBookSettingsKey:v2";

    /**
     * Tin tức trang chủ
     */
    String INDEX_NEWS_KEY = "indexNewsKey";

    /**
     * Bảng lượt xem trang chủ
     */
    String INDEX_CLICK_BANK_BOOK_KEY = "indexClickBankBookKey";

    /**
     * Liên kết bạn bè trang chủ
     */
    String INDEX_LINK_KEY = "indexLinkKey";

    /**
     * Bảng tác phẩm mới trang chủ
     */
    String INDEX_NEW_BOOK_KEY = "indexNewBookKey";


    /**
     * Bảng cập nhật trang chủ
     */
    String INDEX_UPDATE_BOOK_KEY = "indexUpdateBookKey";

    /**
     * Khóa lưu thư mục mẫu
     */
    String TEMPLATE_DIR_KEY = "templateDirKey";
    ;

    /**
     * Tiền tố khóa lưu luồng thu thập đang chạy
     */
    String RUNNING_CRAWL_THREAD_KEY_PREFIX = "runningCrawlTreadDataKeyPrefix";

    /**
     * Thời gian cập nhật công cụ tìm kiếm gần nhất
     */
    String ES_LAST_UPDATE_TIME = "esLastUpdateTime";

    /**
     * Khóa chuyển đổi công cụ tìm kiếm
     */
    String ES_TRANS_LOCK = "esTransLock";

    /**
     * Công cụ tìm kiếm đã cập nhật lượt xem tác phẩm ở lần trước hay chưa
     */
    String ES_IS_UPDATE_VISIT = "esIsUpdateVisit";

    /**
     * Lượt xem tác phẩm tích lũy
     */
    String BOOK_ADD_VISIT_COUNT = "bookAddVisitCount";
    /**
     * Bộ nhớ đệm kiểm thử quy tắc thu thập
     */
    String BOOK_TEST_PARSE = "testParse";
}
