package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SimHashUtilTest {

    @Test
    public void testSimHash64BitFormat() {
        String text = "Tiểu thuyết kiếm hiệp Tiên Niên Thùy Đắc Trường Sinh là một tác phẩm văn học xuất sắc.";
        String simHash = SimHashUtil.getSimHash(text);
        assertNotNull(simHash);
        assertEquals(64, simHash.length());
        assertTrue(simHash.matches("[01]{64}"));
    }

    @Test
    public void testHammingDistanceIdenticalText() {
        String text = "Đoạn văn thử nghiệm thuật toán SimHash trùng lặp 100%.";
        String hash1 = SimHashUtil.getSimHash(text);
        String hash2 = SimHashUtil.getSimHash(text);
        assertEquals(0, SimHashUtil.getHammingDistance(hash1, hash2));
        assertTrue(SimHashUtil.isSimilar(hash1, hash2, 3));
    }

    @Test
    public void testHammingDistanceSimilarText() {
        String text1 = "Tiểu thuyết tiên hiệp hay nhất năm 2026 lấy bối cảnh huyền huyễn tiên giới trung hoa.";
        String text2 = "Tiểu thuyết tiên hiệp hay nhất năm 2026 lấy bối cảnh huyền huyễn tiên giới trung quốc.";
        String hash1 = SimHashUtil.getSimHash(text1);
        String hash2 = SimHashUtil.getSimHash(text2);
        int distance = SimHashUtil.getHammingDistance(hash1, hash2);
        assertTrue(distance <= 5, "Distance for slightly modified text should be small");
    }

    @Test
    public void testHammingDistanceCompletelyDifferentText() {
        String text1 = "Lập trình Java Spring Boot microservices kiến trúc phân tán.";
        String text2 = "Bánh mì Sài Gòn đặc biệt thơm ngon bổ dưỡng buổi sáng.";
        String hash1 = SimHashUtil.getSimHash(text1);
        String hash2 = SimHashUtil.getSimHash(text2);
        int distance = SimHashUtil.getHammingDistance(hash1, hash2);
        assertTrue(distance > 3, "Distance for completely different text should exceed threshold 3");
    }
}
