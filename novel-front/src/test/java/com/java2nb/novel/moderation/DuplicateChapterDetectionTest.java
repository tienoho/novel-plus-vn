package com.java2nb.novel.moderation;

import com.java2nb.novel.core.utils.ContentHashUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DuplicateChapterDetectionTest {

    @Test
    public void testContentHashSha256Consistency() {
        String content1 = "Nội dung chương 1 tác phẩm Tiên Niên Thùy Đắc Trường Sinh.";
        String content2 = "Nội dung chương 1 tác phẩm Tiên Niên Thùy Đắc Trường Sinh.";
        String content3 = "Nội dung chương 2 tác phẩm Tiên Niên Thùy Đắc Trường Sinh.";

        String hash1 = ContentHashUtil.sha256Hex(content1);
        String hash2 = ContentHashUtil.sha256Hex(content2);
        String hash3 = ContentHashUtil.sha256Hex(content3);

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2, "Identical content must yield identical SHA-256 hash");
        assertNotEquals(hash1, hash3, "Different content must yield different SHA-256 hash");
    }
}
