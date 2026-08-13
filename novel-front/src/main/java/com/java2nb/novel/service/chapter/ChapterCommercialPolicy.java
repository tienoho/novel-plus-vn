package com.java2nb.novel.service.chapter;

import lombok.Data;

import java.util.Date;

/** Chính sách thương mại tùy chọn gắn 1-1 với một chương đã xuất bản. */
@Data
public class ChapterCommercialPolicy {
    private Long bookIndexId;
    private Long bookId;
    private Integer customPrice;
    private Date unlockAt;
    private Date freeFrom;
    private Date freeUntil;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
