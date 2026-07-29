package com.java2nb.novel.service.reader;

import lombok.Data;

import java.util.Date;

@Data
public class ReaderAnnotationRow {
    private Long id;
    private Long userId;
    private Long bookId;
    private Long bookIndexId;
    private String type;
    private Integer paragraphIndex;
    private Integer characterOffset;
    private String selectedText;
    private String noteText;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
