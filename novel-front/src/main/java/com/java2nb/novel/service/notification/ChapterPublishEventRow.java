package com.java2nb.novel.service.notification;

import lombok.Data;

/** Snapshot của chương tại thời điểm lần đầu được duyệt. */
@Data
public class ChapterPublishEventRow {
    private Long id;
    private Long chapterId;
    private Long bookId;
    private Long authorId;
    private String bookName;
    private String chapterName;
}

