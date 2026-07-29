package com.java2nb.novel.service.chapter;

import com.java2nb.novel.entity.BookIndex;

import java.util.Date;

public interface ChapterCommercialPolicyService {
    ChapterAccessDecision evaluate(BookIndex chapter, boolean purchased, Date now);

    ChapterCommercialPolicyView getForAuthor(long authorId, long bookIndexId);

    int calculateAutomaticPrice(int wordCount);

    int resolveEffectivePrice(byte isVip, Integer customPrice, int automaticPrice);

    Integer findCustomPrice(long bookIndexId);

    void validate(byte isVip, Integer customPrice, Date unlockAt, Date freeFrom, Date freeUntil);

    void applyPublishedPolicy(long authorId, long bookIndexId, byte isVip, Integer customPrice,
                              Date unlockAt, Date freeFrom, Date freeUntil, int automaticPrice);
}
