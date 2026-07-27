package com.java2nb.novel.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ModerationStatusEnum {
    PENDING((byte) 0, "Chờ duyệt"),
    APPROVED((byte) 1, "Đã duyệt"),
    REJECTED((byte) 2, "Từ chối"),
    TAKEN_DOWN((byte) 3, "Gỡ bài");

    private final Byte code;
    private final String name;

    public static ModerationStatusEnum getByCode(Byte code) {
        if (code == null) {
            return PENDING;
        }
        for (ModerationStatusEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return PENDING;
    }
}
