package com.java2nb.common.config;

public class Constant {

    // Tài khoản hệ thống trình diễn
    public static String DEMO_ACCOUNT = "test";
    // Tự động bỏ tiền tố bảng
    public static String AUTO_REOMVE_PRE = "true";
    // Dừng tác vụ theo lịch
    public static String STATUS_RUNNING_STOP = "stop";
    // Bật tác vụ theo lịch
    public static String STATUS_RUNNING_START = "start";
    // Trạng thái đọc thông báo - chưa đọc
    public static String OA_NOTIFY_READ_NO = "0";
    // Trạng thái đọc thông báo - đã đọc
    public static int OA_NOTIFY_READ_YES = 1;
    // ID nút gốc phòng ban
    public static Long DEPT_ROOT_ID = 0L;
    // Phương thức bộ nhớ đệm
    public static String CACHE_TYPE_REDIS = "redis";

    public static String LOG_ERROR = "error";

    public static final String UPLOAD_FILES_PREFIX = "/files/";

    public static final String BOOK_IS_DOWNLOADING_KEY = "bookIsDownloading:";

}
