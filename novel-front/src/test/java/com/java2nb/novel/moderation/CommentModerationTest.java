package com.java2nb.novel.moderation;

import com.java2nb.novel.core.utils.SensitiveWordFilter;
import com.java2nb.novel.entity.BookComment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CommentModerationTest {

    @BeforeEach
    public void setUp() {
        SensitiveWordFilter.getInstance().addWord("lừa đảo");
    }

    @Test
    public void testCleanCommentApprovedAutomatically() {
        BookComment comment = new BookComment();
        comment.setCommentContent("Truyện này rất hay, tác giả viết cuốn quá!");

        boolean sensitive = SensitiveWordFilter.getInstance().containsSensitiveWord(comment.getCommentContent());
        comment.setAuditStatus(sensitive ? (byte) 0 : (byte) 1);

        assertEquals((byte) 1, comment.getAuditStatus());
    }

    @Test
    public void testSensitiveCommentPendingModeration() {
        BookComment comment = new BookComment();
        comment.setCommentContent("Trang web này là lua dao người đọc!");

        boolean sensitive = SensitiveWordFilter.getInstance().containsSensitiveWord(comment.getCommentContent());
        comment.setAuditStatus(sensitive ? (byte) 0 : (byte) 1);

        assertEquals((byte) 0, comment.getAuditStatus(), "Comment containing sensitive words must be marked pending (0)");
    }
}
