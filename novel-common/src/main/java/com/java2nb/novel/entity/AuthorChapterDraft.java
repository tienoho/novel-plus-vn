package com.java2nb.novel.entity;

import lombok.Data;

import java.util.Date;

/** Bản nháp chương riêng tư, chưa được đưa vào mục lục độc giả. */
@Data
public class AuthorChapterDraft {
    private Long id;
    private String draftNo;
    private String clientKey;
    private Long authorId;
    private Long bookId;
    private Long indexId;
    private String indexName;
    private String content;
    private Byte isVip;
    private Integer bookPrice;
    private Integer customPrice;
    private Date unlockAt;
    private Date freeFrom;
    private Date freeUntil;
    private String status;
    private Long version;
    private Date scheduledAt;
    private Long publishedIndexId;
    private Date lastAutosaveAt;
    private String lastError;
    private Date createTime;
    private Date updateTime;
}
