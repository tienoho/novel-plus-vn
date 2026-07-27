package com.java2nb.novel.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgeRatingEnum {
    ALL((byte) 0, "Tất cả độ tuổi"),
    TEEN_13((byte) 13, "13+"),
    MATURE_16((byte) 16, "16+"),
    ADULT_18((byte) 18, "18+");

    private final Byte code;
    private final String name;

    public static AgeRatingEnum getByCode(Byte code) {
        if (code == null) {
            return ALL;
        }
        for (AgeRatingEnum item : values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return ALL;
    }
}
