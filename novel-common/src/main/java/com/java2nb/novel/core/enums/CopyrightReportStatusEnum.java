package com.java2nb.novel.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CopyrightReportStatusEnum {
    PENDING((byte) 0, "Chờ xử lý"),
    UNDER_REVIEW((byte) 1, "Đang thụ lý"),
    APPROVED_TAKEDOWN((byte) 2, "Đã duyệt gỡ bài"),
    TAKEDOWN_EXECUTED((byte) 2, "Đã duyệt gỡ bài"),
    REJECTED((byte) 3, "Bác bỏ"),
    APPEALED((byte) 4, "Đã kháng nghị"),
    COUNTER_NOTICE_RECEIVED((byte) 4, "Đã tiếp nhận kháng nghị"),
    RESOLVED((byte) 5, "Hoàn tất");

    private final Byte code;
    private final String name;
}
