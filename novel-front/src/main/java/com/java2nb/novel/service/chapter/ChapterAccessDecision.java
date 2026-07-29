package com.java2nb.novel.service.chapter;

/** Kết quả duy nhất dùng cho render, API reader-state và kiểm tra mua chương. */
public record ChapterAccessDecision(boolean purchaseRequired, boolean offlineEligible,
                                    boolean temporaryFree, boolean permanentlyFree) {
}
