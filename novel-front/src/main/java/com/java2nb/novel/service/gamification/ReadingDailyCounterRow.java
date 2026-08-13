package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.time.LocalDate;

@Data
public class ReadingDailyCounterRow {
    private Long userId;
    private LocalDate localDate;
    private Integer verifiedSeconds;
    private Long version;
}
