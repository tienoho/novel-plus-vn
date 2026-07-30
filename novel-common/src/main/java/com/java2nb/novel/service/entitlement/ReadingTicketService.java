package com.java2nb.novel.service.entitlement;

import java.util.Date;

public interface ReadingTicketService {
    ReadingTicketPostResult grant(ReadingTicketGrantCommand command);
    ReadingTicketUnlockResult unlockChapter(ReadingTicketUnlockCommand command);
    ReadingTicketExpiryResult expireDueLots(long userId, Date cutoff, String policyVersion,
                                            int maxLotsPerUser);
    boolean hasActiveChapterEntitlement(long userId, long bookIndexId, Date at);
    ReadingTicketAccountRow getOrCreateAccount(long userId);
    ReadingTicketLedgerPage listLedgerHistory(long userId, int page, int pageSize);
    ReadingTicketLotPage listLotHistory(long userId, int page, int pageSize);
}
