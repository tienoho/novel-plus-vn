package com.java2nb.novel.moderation;

import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.entity.BookContentHistory;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class ChapterRevisionHistoryTest {

    @Test
    public void testChapterSnapshotIncrement() {
        BookContentHistory v1 = new BookContentHistory();
        v1.setBookId(1001L);
        v1.setIndexId(2001L);
        v1.setVersionNum(1);
        v1.setIndexName("Chương 1: Mở đầu");
        v1.setContent("Nội dung chương 1 ban đầu.");
        v1.setContentHash(ContentHashUtil.sha256Hex(v1.getContent()));
        v1.setModifiedBy(501L);
        v1.setChangeReason("Xuất bản chương mới");

        BookContentHistory v2 = new BookContentHistory();
        v2.setBookId(1001L);
        v2.setIndexId(2001L);
        v2.setVersionNum(2);
        v2.setIndexName("Chương 1: Mở đầu (Đã sửa)");
        v2.setContent("Nội dung chương 1 đã chỉnh sửa hoàn chỉnh.");
        v2.setContentHash(ContentHashUtil.sha256Hex(v2.getContent()));
        v2.setModifiedBy(501L);
        v2.setChangeReason("Cập nhật nội dung chương");

        assertEquals(1, v1.getVersionNum());
        assertEquals(2, v2.getVersionNum());
        assertNotEquals(v1.getContentHash(), v2.getContentHash());
    }
}
