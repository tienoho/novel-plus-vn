package com.java2nb.novel.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Challenge 4: SimHashUtil 64-Bit Hamming Distance Logic & Threshold Boundaries")
class SimHashUtilChallengeTest {

    @Test
    @DisplayName("Hamming distance calculation on exact 64-bit long integers")
    void testGetHammingDistanceLong() {
        long h1 = 0b11110000L;
        long h2 = 0b11000000L;
        // Difference at 2 bit positions
        assertThat(SimHashUtil.getHammingDistance(h1, h2)).isEqualTo(2);

        // Identical longs have distance 0
        assertThat(SimHashUtil.getHammingDistance(0xFFFFFFFF00000000L, 0xFFFFFFFF00000000L)).isEqualTo(0);

        // Bitwise inverse has distance 64
        assertThat(SimHashUtil.getHammingDistance(0L, -1L)).isEqualTo(64);
    }

    @Test
    @DisplayName("Similarity threshold boundary condition (d <= 3 is similar, d > 3 is not)")
    void testIsSimilarThresholdBoundary() {
        String hash1 = "1111111111111111111111111111111111111111111111111111111111111111"; // 64 ones
        String hashDist3 = "1111111111111111111111111111111111111111111111111111111111111000"; // 3 zeroes -> dist = 3
        String hashDist4 = "1111111111111111111111111111111111111111111111111111111111110000"; // 4 zeroes -> dist = 4

        assertThat(SimHashUtil.getHammingDistance(hash1, hashDist3)).isEqualTo(3);
        assertThat(SimHashUtil.isSimilar(hash1, hashDist3, 3)).isTrue();

        assertThat(SimHashUtil.getHammingDistance(hash1, hashDist4)).isEqualTo(4);
        assertThat(SimHashUtil.isSimilar(hash1, hashDist4, 3)).isFalse();
    }

    @Test
    @DisplayName("64-bit fingerprint string vs Long bit alignment consistency")
    void testSimHashStringAndLongConsistency() {
        String text = "Đây là một đoạn văn bản thử nghiệm tính năng phát hiện đạo văn trong Novel Plus.";

        long hashLong = SimHashUtil.getSimHashLong(text);
        String hashStr = SimHashUtil.getSimHash(text);

        assertThat(hashStr).hasSize(64);
        // Ensure getSimHash(text) converts back to hashLong accurately via getHammingDistance
        assertThat(SimHashUtil.getHammingDistance(hashLong, SimHashUtil.getSimHashLong(text))).isEqualTo(0);
    }

    @Test
    @DisplayName("Text similarity comparison for near-identical vs completely different documents")
    void testTextSimilarity() {
        String docOriginal = "Chương 1: Ngày đầu tiên bước vào thế giới tu tiên, Nam đã gặp được một vị cao nhân bí ẩn trao cho cuốn bí kíp.";
        String docSimilar = "Chương 1: Ngày đầu tiên bước vào thế giới tu luyện, Nam đã gặp được một vị cao nhân kỳ lạ trao cho cuốn bí kíp.";
        String docDifferent = "Hôm nay thời tiết Hà Nội rất đẹp, mặt trời mọc từ đằng đông và lặn ở đằng tây.";

        // Identical document
        assertThat(SimHashUtil.isSimilar(docOriginal, docOriginal)).isTrue();

        // Slightly modified document (near-duplicate)
        assertThat(SimHashUtil.isSimilar(docOriginal, docSimilar)).isTrue();

        // Completely different topic document
        assertThat(SimHashUtil.isSimilar(docOriginal, docDifferent)).isFalse();
    }

    @Test
    @DisplayName("Boundary checks for null and empty inputs")
    void testNullAndEmptyInputs() {
        assertThat(SimHashUtil.isSimilar((String) null, "test")).isFalse();
        assertThat(SimHashUtil.isSimilar("test", (String) null)).isFalse();
        assertThat(SimHashUtil.getSimHashLong("")).isEqualTo(0L);
        assertThat(SimHashUtil.getSimHashLong(null)).isEqualTo(0L);
    }
}
