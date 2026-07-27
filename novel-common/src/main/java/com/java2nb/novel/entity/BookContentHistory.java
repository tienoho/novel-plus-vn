package com.java2nb.novel.entity;

import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
public class BookContentHistory implements Serializable {
    private Long id;
    private Long bookId;
    private Long indexId;
    private Integer versionNum;
    private String indexName;
    private String content;
    private Integer wordCount;
    private String contentHash;
    private Long modifiedBy;
    private Byte modifiedType; // 1: Author, 2: Admin, 3: System
    private String changeReason;
    private Date createTime;
}
