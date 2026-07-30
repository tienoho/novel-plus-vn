package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.SensitiveWordFilter;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TicketAccountRow;
import com.java2nb.novel.service.gamification.TicketBookEligibilityRow;
import com.java2nb.novel.service.gamification.TicketBookSummary;
import com.java2nb.novel.service.gamification.TicketExpiryResult;
import com.java2nb.novel.service.gamification.TicketGrantCommand;
import com.java2nb.novel.service.gamification.TicketHistoryPage;
import com.java2nb.novel.service.gamification.TicketLedgerRow;
import com.java2nb.novel.service.gamification.TicketLotRow;
import com.java2nb.novel.service.gamification.TicketPolicy;
import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.TicketRankCounterRow;
import com.java2nb.novel.service.gamification.TickerEntryRow;
import com.java2nb.novel.service.gamification.TicketSeasonRow;
import com.java2nb.novel.service.gamification.TicketVoteCommand;
import com.java2nb.novel.service.gamification.TicketVoteResult;
import com.java2nb.novel.service.gamification.TicketVoteRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Sổ cái Ngọn Đuốc.
 *
 * <p>Cấu trúc bám theo {@code WalletLedgerServiceImpl}: kiểm tra idempotency bằng đường nhanh
 * không khoá, ghi bút toán trước để ràng buộc UNIQUE làm trọng tài cuối, rồi mới cập nhật bảng
 * chiếu bằng câu UPDATE có điều kiện. Khác biệt duy nhất về nguyên tắc là Ngọn Đuốc không có
 * trạng thái nợ, nên không có nhánh nào cho phép số dư âm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyTicketServiceImpl implements MonthlyTicketService {

    private static final int MAX_LOT_PAGE_SIZE = 200;
    private static final int MAX_HISTORY_PAGE_SIZE = 100;
    private static final int MAX_TICKER_ENTRIES = 50;

    private final MonthlyTicketMapper monthlyTicketMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TicketPostResult grant(TicketGrantCommand command) {
        String requestHash = grantRequestHash(command);

        // Đường nhanh: lần gửi lại phổ biến nhất không cần chạm khoá hàng nào.
        TicketLedgerRow existing = monthlyTicketMapper
            .selectLedgerByIdempotencyKey(command.idempotencyKey());
        if (existing != null) {
            validateExisting(existing, command, requestHash);
            return TicketPostResult.ALREADY_POSTED;
        }

        TicketAccountRow account = lockAccount(command.userId());
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException("Tài khoản Ngọn Đuốc đang bị đóng băng");
        }

        long balanceAfter = Math.addExact(account.getAvailableBalance(), command.amount());
        String entryNo = "MT-" + UUID.randomUUID().toString().replace("-", "");
        try {
            monthlyTicketMapper.insertLedger(entryNo, command.userId(), "GRANT", command.amount(),
                balanceAfter, command.sourceType(), command.sourceRef(), command.idempotencyKey(),
                requestHash, null, null, null, command.operatorType(), command.operatorId(),
                command.reason(), command.policyVersion());
        } catch (DuplicateKeyException exception) {
            // Một giao dịch song song đã commit giữa đường nhanh và lúc này. Ràng buộc UNIQUE trên
            // idempotency_key là trọng tài; đọc lại và trả kết quả cũ thay vì ghi đè.
            TicketLedgerRow concurrent = monthlyTicketMapper
                .selectLedgerByIdempotencyKey(command.idempotencyKey());
            if (concurrent == null) {
                throw exception;
            }
            validateExisting(concurrent, command, requestHash);
            return TicketPostResult.ALREADY_POSTED;
        }

        TicketLedgerRow posted = monthlyTicketMapper
            .selectLedgerByIdempotencyKey(command.idempotencyKey());
        if (posted == null) {
            throw new IllegalStateException("Không đọc được bút toán Ngọn Đuốc vừa tạo");
        }

        if (monthlyTicketMapper.insertLot(command.userId(), command.sourceType(), command.sourceRef(),
            command.amount(), posted.getId(), command.effectiveAt(), command.expireAt(),
            command.policyVersion()) != 1) {
            throw new IllegalStateException("Không thể tạo lô Ngọn Đuốc");
        }

        if (monthlyTicketMapper.creditAccount(account.getId(), account.getVersion(),
            command.amount()) != 1) {
            throw new IllegalStateException("Tài khoản Ngọn Đuốc đã được cập nhật đồng thời");
        }
        return TicketPostResult.POSTED;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TicketAccountRow getOrCreateAccount(long userId) {
        TicketAccountRow account = monthlyTicketMapper.selectAccount(userId);
        if (account != null) {
            return account;
        }
        monthlyTicketMapper.insertAccountIgnore(userId);
        account = monthlyTicketMapper.selectAccount(userId);
        if (account == null) {
            throw new IllegalStateException("Không thể tạo tài khoản Ngọn Đuốc");
        }
        return account;
    }

    @Override
    public List<TicketLotRow> listActiveLots(long userId, Date now, int limit) {
        Objects.requireNonNull(now, "Thiếu mốc thời gian khi liệt kê lô Ngọn Đuốc");
        int safeLimit = Math.max(1, Math.min(limit, MAX_LOT_PAGE_SIZE));
        return monthlyTicketMapper.selectActiveLots(userId, now, safeLimit);
    }

    @Override
    public TicketHistoryPage listHistory(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_HISTORY_PAGE_SIZE));
        long offset = Math.multiplyExact((long) safePage - 1, safePageSize);
        long total = monthlyTicketMapper.countLedgerByUser(userId);
        return new TicketHistoryPage(monthlyTicketMapper.selectLedgerByUser(userId, offset, safePageSize),
            total, safePage, safePageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TicketExpiryResult expireDueLots(long userId, Date cutoff, String scopeKey,
                                            String policyVersion) {
        if (userId <= 0 || cutoff == null || scopeKey == null || scopeKey.isBlank()
            || policyVersion == null || policyVersion.isBlank()) {
            throw new IllegalArgumentException("Yêu cầu đóng lot Ngọn Đuốc hết hạn không hợp lệ");
        }

        TicketAccountRow account = monthlyTicketMapper.lockAccountByUserId(userId);
        if (account == null) {
            throw new IllegalStateException("Lot Ngọn Đuốc không có tài khoản projection");
        }
        List<TicketLotRow> lots = monthlyTicketMapper.lockExpiredLotsByUser(userId, cutoff);
        if (lots.isEmpty()) {
            return TicketExpiryResult.EMPTY;
        }

        long total = 0;
        StringBuilder lotFingerprint = new StringBuilder();
        for (TicketLotRow lot : lots) {
            total = Math.addExact(total, lot.getRemainingAmount());
            if (!lotFingerprint.isEmpty()) {
                lotFingerprint.append(',');
            }
            lotFingerprint.append(lot.getId()).append(':').append(lot.getVersion())
                .append(':').append(lot.getRemainingAmount());
        }
        if (account.getAvailableBalance() < total) {
            log.error("GAMIFY-ALERT-001 projection Ngọn Đuốc không đủ để đóng lot hết hạn: "
                    + "userId={}, balance={}, amount={}",
                userId, account.getAvailableBalance(), total);
            throw new IllegalStateException("Số dư Ngọn Đuốc thấp hơn tổng lot cần đóng");
        }

        String fingerprint = sha256(lotFingerprint.toString());
        String idempotencyKey = "EXPIRE:" + scopeKey + ':' + userId + ':' + fingerprint.substring(0, 16);
        String businessId = scopeKey + ':' + userId + ':' + fingerprint.substring(0, 16);
        String requestHash = sha256("EXPIRE|" + userId + '|' + scopeKey + '|' + total + '|'
            + lotFingerprint + '|' + policyVersion);
        long balanceAfter = Math.subtractExact(account.getAvailableBalance(), total);
        monthlyTicketMapper.insertLedger("MT-" + UUID.randomUUID().toString().replace("-", ""),
            userId, "EXPIRE", -total, balanceAfter, "LOT_EXPIRY", businessId,
            idempotencyKey, requestHash, null, null, null, "SYSTEM", null,
            "Đóng lot Ngọn Đuốc hết hạn", policyVersion);
        TicketLedgerRow ledger = monthlyTicketMapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán đóng lot Ngọn Đuốc hết hạn");
        }

        for (TicketLotRow lot : lots) {
            if (monthlyTicketMapper.expireLot(lot.getId(), lot.getVersion(), lot.getRemainingAmount(),
                cutoff) != 1) {
                throw new IllegalStateException("Lot Ngọn Đuốc hết hạn đã được cập nhật đồng thời");
            }
            if (monthlyTicketMapper.insertLotAllocation(ledger.getId(), lot.getId(),
                lot.getRemainingAmount(), 0) != 1) {
                throw new IllegalStateException("Không thể ghi phân bổ lot Ngọn Đuốc hết hạn");
            }
        }
        if (monthlyTicketMapper.expireFromAccount(account.getId(), account.getVersion(), total) != 1) {
            throw new IllegalStateException("Không thể cập nhật projection khi Đuốc hết hạn");
        }
        return new TicketExpiryResult(lots.size(), total);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public TicketVoteResult castVote(TicketVoteCommand command, TicketPolicy policy) {
        Objects.requireNonNull(command, "Thiếu yêu cầu thắp đuốc");
        Objects.requireNonNull(policy, "Thiếu chính sách thắp đuốc");
        if (command.count() > policy.maxTicketsPerRequest()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_VOTE_DAILY_LIMIT);
        }

        TicketVoteRow replay = monthlyTicketMapper.selectVoteByUserClientRequest(
            command.userId(), command.clientRequestId());
        if (replay != null) {
            validateExistingVote(replay, command);
            return replayResult(replay);
        }

        TicketSeasonRow season = monthlyTicketMapper.selectOpenSeason(command.occurredAt());
        if (season == null || !Objects.equals(season.getPolicyVersion(), policy.policyVersion())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_SEASON_UNAVAILABLE);
        }

        TicketAccountRow account = lockAccount(command.userId());
        // Đường nhanh ở trên có thể chạy trước khi request song song commit. Khoá account đã
        // tuần tự hoá mọi request của cùng user, nên phải đọc lại trước khi chạm quota.
        replay = monthlyTicketMapper.selectVoteByUserClientRequest(
            command.userId(), command.clientRequestId());
        if (replay != null) {
            validateExistingVote(replay, command);
            return replayResult(replay);
        }
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_ACCOUNT_UNAVAILABLE);
        }
        if (account.getAvailableBalance() < command.count()) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_TICKET_INSUFFICIENT);
        }

        TicketBookEligibilityRow book = monthlyTicketMapper.selectBookEligibility(
            command.bookId(), command.userId());
        String ineligibleReason = ineligibleReason(book, policy);
        if (ineligibleReason != null) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_BOOK_INELIGIBLE);
        }

        monthlyTicketMapper.insertDailyCounterIgnore(command.userId(), command.localDate());
        if (monthlyTicketMapper.tryConsumeDailyQuota(command.userId(), command.localDate(), command.count(),
            policy.maxVotesPerDay(), policy.maxTicketsPerDay()) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_VOTE_DAILY_LIMIT);
        }

        monthlyTicketMapper.insertBookQuotaIgnore(command.userId(), season.getId(), command.bookId());
        if (monthlyTicketMapper.tryConsumeBookQuota(command.userId(), season.getId(), command.bookId(),
            command.count(), policy.maxTicketsPerBookPerSeason()) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_VOTE_BOOK_LIMIT);
        }

        String idempotencyKey = voteIdempotencyKey(command, season.getId());
        String requestHash = voteRequestHash(command, season.getId(), policy.policyVersion());
        long balanceAfter = Math.subtractExact(account.getAvailableBalance(), command.count());
        String businessId = season.getId() + ":" + command.bookId() + ":" + command.clientRequestId();
        try {
            monthlyTicketMapper.insertLedger("MT-" + UUID.randomUUID().toString().replace("-", ""),
                command.userId(), "SPEND", -command.count(), balanceAfter, "BOOK_VOTE", businessId,
                idempotencyKey, requestHash, season.getId(), command.bookId(), null, "USER",
                command.userId(), null, policy.policyVersion());
        } catch (DuplicateKeyException exception) {
            // Sau khi đã khoá account và đọc lại vote, một request cùng user không thể thắng
            // race ở đây. Không nuốt lỗi vì quota đã được cập nhật trong transaction hiện tại;
            // ném lại để rollback toàn bộ, lần retry kế tiếp sẽ đi qua đường replay an toàn.
            throw exception;
        }

        TicketLedgerRow ledger = monthlyTicketMapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán thắp đuốc vừa tạo");
        }

        long need = command.count();
        List<TicketLotRow> lots = monthlyTicketMapper.lockSpendableLots(
            command.userId(), command.occurredAt(), policy.maxLotsPerSpend());
        for (TicketLotRow lot : lots) {
            if (need == 0) {
                break;
            }
            long take = Math.min(need, lot.getRemainingAmount());
            long remainingAfter = Math.subtractExact(lot.getRemainingAmount(), take);
            if (monthlyTicketMapper.consumeLot(lot.getId(), lot.getVersion(), take,
                command.occurredAt()) != 1) {
                throw new IllegalStateException("Lô Ngọn Đuốc đã được cập nhật đồng thời");
            }
            if (monthlyTicketMapper.insertLotAllocation(ledger.getId(), lot.getId(), take,
                remainingAfter) != 1) {
                throw new IllegalStateException("Không thể ghi phân bổ lô Ngọn Đuốc");
            }
            need -= take;
        }
        if (need != 0) {
            log.error("GAMIFY-ALERT-001 projection Ngọn Đuốc lệch lot: userId={}, balance={}, thiếu={}",
                command.userId(), account.getAvailableBalance(), need);
            throw new IllegalStateException("Số dư Ngọn Đuốc không khớp với các lô còn hiệu lực");
        }

        if (monthlyTicketMapper.debitAccount(account.getId(), account.getVersion(), command.count()) != 1) {
            throw new IllegalStateException("Tài khoản Ngọn Đuốc đã được cập nhật đồng thời");
        }
        if (monthlyTicketMapper.insertVote(season.getId(), command.bookId(), book.getAuthorId(),
            command.userId(), command.count(), ledger.getId(), idempotencyKey, requestHash,
            command.clientRequestId(), command.sourceIpHash(), policy.policyVersion()) != 1) {
            throw new IllegalStateException("Không thể ghi lượt thắp đuốc");
        }

        int newDistinctVoter = monthlyTicketMapper.insertRankVoterIgnore(
            season.getId(), command.bookId(), command.userId());
        if (monthlyTicketMapper.upsertRankCounter(season.getId(), command.bookId(), command.count(),
            newDistinctVoter, command.occurredAt()) <= 0) {
            throw new IllegalStateException("Không thể cập nhật bộ đếm xếp hạng Ngọn Đuốc");
        }
        if (monthlyTicketMapper.countSeasonStillOpen(season.getId(), command.occurredAt()) != 1) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_SEASON_UNAVAILABLE);
        }

        TicketVoteRow vote = monthlyTicketMapper.selectVoteByIdempotencyKey(idempotencyKey);
        TicketRankCounterRow counter = monthlyTicketMapper.selectRankCounter(season.getId(), command.bookId());
        if (vote == null || counter == null) {
            throw new IllegalStateException("Không đọc được kết quả thắp đuốc vừa ghi");
        }
        return new TicketVoteResult(TicketPostResult.POSTED, vote.getId(), counter.getTotalTickets(),
            balanceAfter);
    }

    @Override
    public TicketBookSummary getBookSummary(long userId, long bookId, Date now, TicketPolicy policy) {
        Objects.requireNonNull(now, "Thiếu mốc thời gian tra cứu Ngọn Đuốc");
        Objects.requireNonNull(policy, "Thiếu chính sách thắp đuốc");
        TicketSeasonRow season = monthlyTicketMapper.selectOpenSeason(now);
        if (season == null) {
            return new TicketBookSummary(null, null, "CLOSED", 0, false, "NO_OPEN_SEASON");
        }
        TicketBookEligibilityRow book = monthlyTicketMapper.selectBookEligibility(bookId, userId);
        String reason = ineligibleReason(book, policy);
        TicketRankCounterRow counter = monthlyTicketMapper.selectRankCounter(season.getId(), bookId);
        long total = counter == null ? 0 : counter.getTotalTickets();
        return new TicketBookSummary(season.getId(), season.getPeriodCode(), season.getStatus(), total,
            reason == null, reason);
    }

    @Override
    public List<TickerEntryRow> listTicker(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_TICKER_ENTRIES));
        // Truy vấn dư một chút để bù các dòng bị lọc bỏ vì trúng từ nhạy cảm, nhưng vẫn giới hạn
        // để không quét không giới hạn khi cả trang toàn nickname bị chặn.
        List<TickerEntryRow> candidates = monthlyTicketMapper.selectRecentTickerEntries(safeLimit * 2);
        List<TickerEntryRow> filtered = new java.util.ArrayList<>();
        for (TickerEntryRow row : candidates) {
            if (filtered.size() >= safeLimit) {
                break;
            }
            String nickname = row.getNickname();
            if (nickname == null || nickname.isBlank()
                || SensitiveWordFilter.getInstance().containsSensitiveWord(nickname)) {
                continue;
            }
            filtered.add(row);
        }
        return filtered;
    }

    private TicketAccountRow lockAccount(long userId) {
        TicketAccountRow account = monthlyTicketMapper.lockAccountByUserId(userId);
        if (account != null) {
            return account;
        }
        monthlyTicketMapper.insertAccountIgnore(userId);
        account = monthlyTicketMapper.lockAccountByUserId(userId);
        if (account == null) {
            throw new IllegalStateException("Không thể khóa tài khoản Ngọn Đuốc");
        }
        return account;
    }

    private void validateExisting(TicketLedgerRow existing, TicketGrantCommand command,
                                  String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)
            || !Objects.equals(existing.getUserId(), command.userId())
            || !Objects.equals(existing.getAmount(), command.amount())
            || !Objects.equals(existing.getBusinessType(), command.sourceType())
            || !Objects.equals(existing.getBusinessId(), command.sourceRef())
            || !Objects.equals(existing.getOperatorType(), command.operatorType())
            || !Objects.equals(existing.getOperatorId(), command.operatorId())
            || !Objects.equals(existing.getReason(), command.reason())
            || !Objects.equals(existing.getPolicyVersion(), command.policyVersion())
            || !"GRANT".equals(existing.getEntryType())) {
            throw new IllegalStateException("Khóa idempotency đã được dùng cho nội dung khác");
        }
    }

    private String ineligibleReason(TicketBookEligibilityRow book, TicketPolicy policy) {
        if (book == null) {
            return "NOT_FOUND";
        }
        if (book.getAuditStatus() == null || book.getAuditStatus() != 1) {
            return "NOT_APPROVED";
        }
        if (book.getStatus() == null || book.getStatus() != 1) {
            return "NOT_PUBLISHED";
        }
        if (book.getAuthorId() == null || book.getOwnerUserId() == null) {
            return "AUTHOR_UNLINKED";
        }
        if (!policy.allowCrawledBooks()
            && (book.getCrawlSourceId() != null || book.getCrawlBookId() != null)) {
            return "CRAWLED";
        }
        if (Boolean.TRUE.equals(book.getBlocked())) {
            return "BLOCKED";
        }
        if (Boolean.TRUE.equals(book.getSelfOwned())) {
            return "SELF_OWNED";
        }
        if (Boolean.TRUE.equals(book.getCollaborator())) {
            return "COLLABORATOR";
        }
        return null;
    }

    private TicketVoteResult replayResult(TicketVoteRow vote) {
        TicketRankCounterRow counter = monthlyTicketMapper.selectRankCounter(vote.getSeasonId(), vote.getBookId());
        TicketAccountRow account = monthlyTicketMapper.selectAccount(vote.getUserId());
        if (counter == null || account == null) {
            throw new IllegalStateException("Không đọc được kết quả thắp đuốc đã ghi trước đó");
        }
        return new TicketVoteResult(TicketPostResult.ALREADY_POSTED, vote.getId(),
            counter.getTotalTickets(), account.getAvailableBalance());
    }

    private void validateExistingVote(TicketVoteRow existing, TicketVoteCommand command) {
        if (!Objects.equals(existing.getUserId(), command.userId())
            || !Objects.equals(existing.getBookId(), command.bookId())
            || !Objects.equals(existing.getTicketCount(), (long) command.count())
            || !Objects.equals(existing.getClientRequestId(), command.clientRequestId())) {
            throw new BusinessException(ResponseStatus.GAMIFICATION_IDEMPOTENCY_CONFLICT);
        }
    }

    private String voteIdempotencyKey(TicketVoteCommand command, long seasonId) {
        return "VOTE:" + command.userId() + ':' + seasonId + ':' + command.bookId() + ':'
            + command.clientRequestId();
    }

    private String voteRequestHash(TicketVoteCommand command, long seasonId, String policyVersion) {
        String canonical = "SPEND|" + command.userId() + '|' + seasonId + '|' + command.bookId()
            + '|' + command.count() + '|' + command.clientRequestId() + '|' + policyVersion;
        return sha256(canonical);
    }

    /**
     * Băm nội dung yêu cầu để phát hiện việc tái sử dụng khoá idempotency cho nội dung khác. Dạng
     * chuẩn hoá dùng dấu gạch đứng giống {@code WalletLedgerServiceImpl.requestHash()} để hai sổ
     * cái có cùng quy ước khi cần đối chiếu thủ công.
     */
    private String grantRequestHash(TicketGrantCommand command) {
        String canonical = "GRANT|" + command.userId() + '|' + command.amount()
            + '|' + command.sourceType() + '|' + command.sourceRef()
            + '|' + command.effectiveAt().getTime() + '|' + command.expireAt().getTime()
            + '|' + command.operatorType() + '|' + Objects.toString(command.operatorId(), "")
            + '|' + Objects.toString(command.reason(), "") + '|' + command.policyVersion();
        return sha256(canonical);
    }

    private String sha256(String canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }
}
