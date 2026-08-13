package com.java2nb.novel.service.chapter;

import java.util.Date;

/** Dữ liệu tác giả cần để chỉnh chính sách mà không lộ nội dung chương. */
public record ChapterCommercialPolicyView(long bookIndexId, byte isVip, int bookPrice,
                                          Integer customPrice, Date unlockAt,
                                          Date freeFrom, Date freeUntil) {
}
