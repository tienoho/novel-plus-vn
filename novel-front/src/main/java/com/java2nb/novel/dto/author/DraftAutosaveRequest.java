package com.java2nb.novel.dto.author;

import lombok.Data;

import java.util.Date;

@Data
public class DraftAutosaveRequest {
    private Long draftId;
    private String clientKey;
    private Long bookId;
    private Long indexId;
    private String indexName;
    private String content;
    private Byte isVip;
    private Integer customPrice;
    private Date unlockAt;
    private Date freeFrom;
    private Date freeUntil;
    private Long expectedVersion;
}
