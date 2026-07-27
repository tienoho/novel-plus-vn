package com.java2nb.novel.dto.author;

import lombok.Data;

import java.util.Date;

@Data
public class DraftScheduleRequest {
    private Long expectedVersion;
    private Date scheduledAt;
}
