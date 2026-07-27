package com.java2nb.novel.copyright;

import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.entity.BookOwnershipProof;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OwnershipProofUploadTest {

    @Test
    public void testProofHashCalculation() {
        BookOwnershipProof proof = new BookOwnershipProof();
        proof.setBookId(1001L);
        proof.setAuthorId(501L);
        proof.setProofType((byte) 1); // 1: Bản thảo gốc
        proof.setNote("Nội dung toàn bộ bản thảo gốc tác phẩm Tiên Niên Thùy Đắc Trường Sinh.");

        String hash = ContentHashUtil.sha256Hex(proof.getNote());
        proof.setFileHash(hash);

        assertNotNull(proof.getFileHash());
        assertEquals(64, proof.getFileHash().length());
        assertEquals(hash, proof.getFileHash());
    }
}
