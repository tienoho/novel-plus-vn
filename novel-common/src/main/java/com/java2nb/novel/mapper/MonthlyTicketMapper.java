package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.TicketAccountRow;
import com.java2nb.novel.service.gamification.TicketBookEligibilityRow;
import com.java2nb.novel.service.gamification.TicketLedgerRow;
import com.java2nb.novel.service.gamification.TicketLotRow;
import com.java2nb.novel.service.gamification.TicketRankCounterRow;
import com.java2nb.novel.service.gamification.TicketSeasonRow;
import com.java2nb.novel.service.gamification.TicketVoteRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Mapper
public interface MonthlyTicketMapper {

    int insertAccountIgnore(@Param("userId") long userId);

    TicketAccountRow selectAccount(@Param("userId") long userId);

    /**
     * Khoá hàng tài khoản. Đây là bước đầu tiên và bắt buộc của mọi đường ghi chạm Ngọn Đuốc:
     * nó khiến hai giao dịch của cùng một người tuần tự hoá hoàn toàn, còn hai người khác nhau
     * thì có tập khoá rời nhau nên không thể khoá chéo.
     */
    TicketAccountRow lockAccountByUserId(@Param("userId") long userId);

    int insertLedger(@Param("entryNo") String entryNo,
                     @Param("userId") long userId,
                     @Param("entryType") String entryType,
                     @Param("amount") long amount,
                     @Param("balanceAfter") long balanceAfter,
                     @Param("businessType") String businessType,
                     @Param("businessId") String businessId,
                     @Param("idempotencyKey") String idempotencyKey,
                     @Param("requestHash") String requestHash,
                     @Param("seasonId") Long seasonId,
                     @Param("bookId") Long bookId,
                     @Param("reversalOfLedgerId") Long reversalOfLedgerId,
                     @Param("operatorType") String operatorType,
                     @Param("operatorId") Long operatorId,
                     @Param("reason") String reason,
                     @Param("policyVersion") String policyVersion);

    TicketLedgerRow selectLedgerByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    int insertLot(@Param("userId") long userId,
                  @Param("sourceType") String sourceType,
                  @Param("sourceRef") String sourceRef,
                  @Param("grantedAmount") long grantedAmount,
                  @Param("grantLedgerId") long grantLedgerId,
                  @Param("effectiveAt") Date effectiveAt,
                  @Param("expireAt") Date expireAt,
                  @Param("policyVersion") String policyVersion);

    /**
     * Cộng số dư khi cấp. Điều kiện {@code version} là khoá lạc quan; trả về 0 nghĩa là một giao
     * dịch khác đã ghi xen vào giữa lúc đọc và lúc ghi.
     */
    int creditAccount(@Param("accountId") long accountId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("amount") long amount);

    List<TicketLotRow> selectActiveLots(@Param("userId") long userId,
                                        @Param("now") Date now,
                                        @Param("limit") int limit);

    long countLedgerByUser(@Param("userId") long userId);

    List<TicketLedgerRow> selectLedgerByUser(@Param("userId") long userId,
                                             @Param("offset") long offset,
                                             @Param("limit") int limit);

    List<Long> selectExpiredLotUserIds(@Param("cutoff") Date cutoff,
                                       @Param("afterUserId") long afterUserId,
                                       @Param("limit") int limit);

    List<TicketLotRow> lockExpiredLotsByUser(@Param("userId") long userId,
                                             @Param("cutoff") Date cutoff);

    int expireLot(@Param("lotId") long lotId,
                  @Param("expectedVersion") long expectedVersion,
                  @Param("expectedRemaining") long expectedRemaining,
                  @Param("closedAt") Date closedAt);

    int expireFromAccount(@Param("accountId") long accountId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("amount") long amount);

    TicketSeasonRow selectOpenSeasonById(@Param("seasonId") long seasonId,
                                         @Param("at") Date at);

    int countSeasonStillOpen(@Param("seasonId") long seasonId, @Param("at") Date at);

    TicketBookEligibilityRow selectBookEligibility(@Param("bookId") long bookId,
                                                    @Param("userId") long userId);

    TicketVoteRow selectVoteByUserClientRequest(@Param("userId") long userId,
                                                @Param("clientRequestId") String clientRequestId);

    TicketVoteRow selectVoteByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    int insertDailyCounterIgnore(@Param("userId") long userId, @Param("localDate") LocalDate localDate);

    int tryConsumeDailyQuota(@Param("userId") long userId,
                             @Param("localDate") LocalDate localDate,
                             @Param("ticketCount") long ticketCount,
                             @Param("maxVotes") int maxVotes,
                             @Param("maxTickets") int maxTickets);

    int insertBookQuotaIgnore(@Param("userId") long userId,
                              @Param("seasonId") long seasonId,
                              @Param("bookId") long bookId);

    int tryConsumeBookQuota(@Param("userId") long userId,
                            @Param("seasonId") long seasonId,
                            @Param("bookId") long bookId,
                            @Param("ticketCount") long ticketCount,
                            @Param("maxTickets") int maxTickets);

    List<TicketLotRow> lockSpendableLots(@Param("userId") long userId,
                                         @Param("at") Date at,
                                         @Param("limit") int limit);

    int consumeLot(@Param("lotId") long lotId,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("amount") long amount,
                   @Param("at") Date at);

    int insertLotAllocation(@Param("ledgerId") long ledgerId,
                            @Param("lotId") long lotId,
                            @Param("amount") long amount,
                            @Param("remainingAfter") long remainingAfter);

    int debitAccount(@Param("accountId") long accountId,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("amount") long amount);

    int insertVote(@Param("seasonId") long seasonId,
                   @Param("bookId") long bookId,
                   @Param("authorId") long authorId,
                   @Param("userId") long userId,
                   @Param("ticketCount") long ticketCount,
                   @Param("ledgerId") long ledgerId,
                   @Param("idempotencyKey") String idempotencyKey,
                   @Param("requestHash") String requestHash,
                   @Param("clientRequestId") String clientRequestId,
                   @Param("sourceIpHash") String sourceIpHash,
                   @Param("sourceDeviceHash") String sourceDeviceHash,
                   @Param("policyVersion") String policyVersion);

    int insertRankVoterIgnore(@Param("seasonId") long seasonId,
                              @Param("bookId") long bookId,
                              @Param("userId") long userId);

    int upsertRankCounter(@Param("seasonId") long seasonId,
                          @Param("bookId") long bookId,
                          @Param("ticketCount") long ticketCount,
                          @Param("newDistinctVoter") int newDistinctVoter,
                          @Param("voteAt") Date voteAt);

    TicketRankCounterRow selectRankCounter(@Param("seasonId") long seasonId,
                                           @Param("bookId") long bookId);

    int insertJobRunIgnore(@Param("jobType") String jobType,
                           @Param("scopeType") String scopeType,
                           @Param("scopeKey") String scopeKey,
                           @Param("ownerInstance") String ownerInstance,
                           @Param("startedAt") Date startedAt);

    int claimStaleJobRun(@Param("jobType") String jobType,
                         @Param("scopeType") String scopeType,
                         @Param("scopeKey") String scopeKey,
                         @Param("ownerInstance") String ownerInstance,
                         @Param("claimedAt") Date claimedAt,
                         @Param("staleBefore") Date staleBefore);

    String selectJobCheckpoint(@Param("jobType") String jobType,
                               @Param("scopeType") String scopeType,
                               @Param("scopeKey") String scopeKey,
                               @Param("ownerInstance") String ownerInstance);

    int advanceJobCheckpoint(@Param("jobType") String jobType,
                             @Param("scopeType") String scopeType,
                             @Param("scopeKey") String scopeKey,
                             @Param("ownerInstance") String ownerInstance,
                             @Param("checkpoint") String checkpoint,
                             @Param("processedDelta") long processedDelta,
                             @Param("heartbeatAt") Date heartbeatAt);

    int completeJobRun(@Param("jobType") String jobType,
                       @Param("scopeType") String scopeType,
                       @Param("scopeKey") String scopeKey,
                       @Param("ownerInstance") String ownerInstance,
                       @Param("finishedAt") Date finishedAt);

    int failJobRun(@Param("jobType") String jobType,
                   @Param("scopeType") String scopeType,
                   @Param("scopeKey") String scopeKey,
                   @Param("ownerInstance") String ownerInstance,
                   @Param("finishedAt") Date finishedAt,
                   @Param("errorMessage") String errorMessage);

    /** Đối soát: số dư projection phải bằng tổng số dư còn lại của các lô đang hiệu lực. */
    List<Map<String, Object>> checkAccountLotDrift();

    /** Đối soát: tổng phân bổ lô phải bằng trị tuyệt đối của bút toán tiêu, hết hạn hoặc thu hồi. */
    List<Map<String, Object>> checkAllocationImbalance();

    /** Đối soát: bút toán cấp không sinh lô, hoặc lô trỏ tới bút toán không tồn tại. */
    List<Map<String, Object>> checkOrphanLots();

    /**
     * Đối soát: job mất heartbeat hoặc kỳ nằm ở REVIEW quá thời hạn cấu hình.
     *
     * <p>Nhận cutoff dạng {@link java.util.Date} đã tính sẵn ở Java, không nhận số giây/giờ để
     * SQL tự cộng trừ. {@code TIMESTAMPADD(SECOND, -#{param}, ...)} với tham số động khiến bộ
     * phân tích SQL của ShardingSphere hiểu nhầm {@code SECOND} là tên cột và ném
     * {@code ColumnNotFoundException}, dù cùng câu lệnh chạy đúng trên MySQL thuần.
     */
    List<Map<String, Object>> checkStuckJobs(@Param("jobCutoff") java.util.Date jobCutoff,
                                             @Param("reviewCutoff") java.util.Date reviewCutoff);

    /** Đối soát: thưởng vẫn chờ release sau khi cửa sổ khiếu nại đã hết. */
    List<Map<String, Object>> checkPendingRewards(@Param("claimCutoff") java.util.Date claimCutoff);

    /**
     * Vote gần nhất của người dùng đã chọn tham gia ticker công khai
     * ({@code gamification_profile.ticker_opt_out = 0}). Mặc định của cột là opt-out, nên hàng
     * chỉ xuất hiện khi người dùng bật tường minh.
     */
    List<com.java2nb.novel.service.gamification.TickerEntryRow> selectRecentTickerEntries(
        @Param("limit") int limit);
}
