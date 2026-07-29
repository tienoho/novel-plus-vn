package com.java2nb.novel.service.ai;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorAiUsageRow {
    private Long id;
    private Long bookId;
    private Long ownerAuthorId;
    private Long actorAuthorId;
    private Long draftId;
    private String operation;
    private String modelName;
    private String inputSha256;
    private String outputSha256;
    private Integer inputChars;
    private Integer outputChars;
    private Integer contextItems;
    private Date createTime;
}
