package com.java2nb.novel.service.notification;

import lombok.Data;

import java.util.Date;

/** Một mục trong hộp thông báo của độc giả. */
@Data
public class UserNotificationRow {
    private Long id;
    private String notificationType;
    private Long bookId;
    private Long authorId;
    private Long chapterId;
    private String bookName;
    private String chapterName;
    private Boolean read;
    private Date readTime;
    private Date createTime;
}

