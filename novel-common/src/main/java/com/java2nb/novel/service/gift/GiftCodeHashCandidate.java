package com.java2nb.novel.service.gift;

public record GiftCodeHashCandidate(String keyId, String codeHash) {
    public GiftCodeHashCandidate {
        if (keyId == null || !keyId.matches("[A-Za-z0-9._-]{1,32}")
            || codeHash == null || !codeHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Ứng viên HMAC mã quà không hợp lệ");
        }
    }
}
