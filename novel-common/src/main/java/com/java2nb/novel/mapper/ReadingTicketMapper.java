package com.java2nb.novel.mapper;

import com.java2nb.novel.service.entitlement.ChapterEntitlementRow;
import com.java2nb.novel.service.entitlement.ReadingTicketAccountRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerWrite;
import com.java2nb.novel.service.entitlement.ReadingTicketLotHistoryRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLotRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ReadingTicketMapper {
    int insertAccountIgnore(@Param("userId") long userId);
    ReadingTicketAccountRow selectAccount(@Param("userId") long userId);
    ReadingTicketAccountRow lockAccountByUserId(@Param("userId") long userId);
    int insertLedger(@Param("entry") ReadingTicketLedgerWrite entry);
    ReadingTicketLedgerRow selectLedgerByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);
    int insertLot(@Param("userId") long userId, @Param("sourceType") String sourceType,
                  @Param("sourceRef") String sourceRef, @Param("grantedAmount") long grantedAmount,
                  @Param("grantLedgerId") long grantLedgerId, @Param("effectiveAt") Date effectiveAt,
                  @Param("expireAt") Date expireAt, @Param("policyVersion") String policyVersion);
    int creditAccount(@Param("accountId") long accountId, @Param("expectedVersion") long expectedVersion,
                      @Param("amount") long amount);
    List<ReadingTicketLotRow> lockSpendableLots(@Param("userId") long userId,
                                                @Param("at") Date at, @Param("limit") int limit);
    List<ReadingTicketLotRow> lockExpiredLotsByUser(@Param("userId") long userId,
                                                    @Param("cutoff") Date cutoff,
                                                    @Param("limit") int limit);
    int consumeLot(@Param("lotId") long lotId, @Param("expectedVersion") long expectedVersion,
                   @Param("amount") long amount, @Param("at") Date at);
    int insertLotAllocation(@Param("ledgerId") long ledgerId, @Param("lotId") long lotId,
                            @Param("amount") long amount, @Param("remainingAfter") long remainingAfter);
    int debitAccount(@Param("accountId") long accountId, @Param("expectedVersion") long expectedVersion,
                     @Param("amount") long amount);
    int expireLot(@Param("lotId") long lotId, @Param("expectedVersion") long expectedVersion,
                  @Param("expectedRemaining") long expectedRemaining,
                  @Param("closedAt") Date closedAt);
    int expireFromAccount(@Param("accountId") long accountId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("amount") long amount);
    List<Long> selectExpiredLotUserIds(@Param("cutoff") Date cutoff, @Param("limit") int limit);
    ChapterEntitlementRow selectActiveEntitlement(@Param("userId") long userId,
                                                  @Param("bookIndexId") long bookIndexId,
                                                  @Param("at") Date at);
    ChapterEntitlementRow selectActiveEntitlementForUpdate(@Param("userId") long userId,
                                                           @Param("bookIndexId") long bookIndexId,
                                                           @Param("at") Date at);
    int insertChapterEntitlement(@Param("userId") long userId, @Param("bookId") long bookId,
                                 @Param("bookIndexId") long bookIndexId,
                                 @Param("sourceType") String sourceType, @Param("sourceId") String sourceId,
                                 @Param("validFrom") Date validFrom, @Param("validUntil") Date validUntil,
                                 @Param("idempotencyKey") String idempotencyKey,
                                 @Param("policyVersion") String policyVersion);
    long countLedgerByUser(@Param("userId") long userId);
    List<ReadingTicketLedgerRow> selectLedgerByUser(@Param("userId") long userId,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);
    long countLotsByUser(@Param("userId") long userId);
    List<ReadingTicketLotHistoryRow> selectLotsByUser(@Param("userId") long userId,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);
}
