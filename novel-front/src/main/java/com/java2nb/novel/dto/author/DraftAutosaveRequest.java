package com.java2nb.novel.dto.author;

import lombok.Data;

@Data
public class DraftAutosaveRequest {
    private Long draftId;
    private String clientKey;
    private Long bookId;
    private Long indexId;
    private String indexName;
    private String content;
    private Byte isVip;
    private Long expectedVersion;
}
