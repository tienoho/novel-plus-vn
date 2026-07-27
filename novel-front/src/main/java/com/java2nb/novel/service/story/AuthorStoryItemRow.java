package com.java2nb.novel.service.story;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorStoryItemRow {
    private Long id;
    private Long authorId;
    private Long bookId;
    private String type;
    private String title;
    private String content;
    private String timelineLabel;
    private Integer sortOrder;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
