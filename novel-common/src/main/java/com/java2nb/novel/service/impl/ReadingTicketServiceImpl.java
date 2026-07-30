package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.ReadingTicketMapper;
import com.java2nb.novel.service.entitlement.ChapterEntitlementRow;
import com.java2nb.novel.service.entitlement.InsufficientReadingTicketException;
import com.java2nb.novel.service.entitlement.ReadingTicketAccountRow;
import com.java2nb.novel.service.entitlement.ReadingTicketGrantCommand;
import com.java2nb.novel.service.entitlement.ReadingTicketExpiryResult;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerPage;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerRow;
import com.java2nb.novel.service.entitlement.ReadingTicketLedgerWrite;
import com.java2nb.novel.service.entitlement.ReadingTicketLotPage;
import com.java2nb.novel.service.entitlement.ReadingTicketLotRow;
import com.java2nb.novel.service.entitlement.ReadingTicketPostResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.entitlement.ReadingTicketUnlockCommand;
import com.java2nb.novel.service.entitlement.ReadingTicketUnlockResult;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class ReadingTicketServiceImpl implements ReadingTicketService {
    private static final int MAX_HISTORY_PAGE_SIZE = 100;

    private final ReadingTicketMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingTicketPostResult grant(ReadingTicketGrantCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu cấp Vé đọc");
        String requestHash = grantRequestHash(command);
        ReadingTicketLedgerRow existing = mapper.selectLedgerByIdempotencyKey(command.idempotencyKey());
        if (existing != null) {
            validateExistingGrant(existing, command, requestHash);
            return ReadingTicketPostResult.ALREADY_POSTED;
        }
        ReadingTicketAccountRow account = lockAccount(command.userId());
        requireActive(account);
        long balanceAfter = Math.addExact(account.getAvailableBalance(), command.amount());
        ReadingTicketLedgerWrite write = ReadingTicketLedgerWrite.builder()
            .entryNo(entryNo()).userId(command.userId()).entryType("GRANT").amount(command.amount())
            .balanceAfter(balanceAfter).businessType(command.sourceType()).businessId(command.sourceRef())
            .idempotencyKey(command.idempotencyKey()).requestHash(requestHash)
            .operatorType(command.operatorType()).operatorId(command.operatorId()).reason(command.reason())
            .policyVersion(command.policyVersion()).build();
        try {
            if (mapper.insertLedger(write) != 1) {
                throw new IllegalStateException("Không thể ghi sổ Vé đọc");
            }
        } catch (DuplicateKeyException exception) {
            ReadingTicketLedgerRow concurrent = mapper.selectLedgerByIdempotencyKey(command.idempotencyKey());
            if (concurrent == null) {
                throw exception;
            }
            validateExistingGrant(concurrent, command, requestHash);
            return ReadingTicketPostResult.ALREADY_POSTED;
        }
        ReadingTicketLedgerRow ledger = requireLedger(command.idempotencyKey());
        if (mapper.insertLot(command.userId(), command.sourceType(), command.sourceRef(), command.amount(),
            ledger.getId(), command.effectiveAt(), command.expireAt(), command.policyVersion()) != 1) {
            throw new IllegalStateException("Không thể tạo lô Vé đọc");
        }
        if (mapper.creditAccount(account.getId(), account.getVersion(), command.amount()) != 1) {
            throw new IllegalStateException("Tài khoản Vé đọc đã được cập nhật đồng thời");
        }
        return ReadingTicketPostResult.POSTED;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingTicketUnlockResult unlockChapter(ReadingTicketUnlockCommand command) {
        Objects.requireNonNull(command, "Thiếu yêu cầu mở chương bằng Vé đọc");
        ChapterEntitlementRow existingEntitlement = mapper.selectActiveEntitlement(
            command.userId(), command.bookIndexId(), command.occurredAt());
        if (existingEntitlement != null) {
            return existingEntitlementResult(command.userId(), existingEntitlement);
        }
        ReadingTicketAccountRow account = lockAccount(command.userId());
        requireActive(account);
        existingEntitlement = mapper.selectActiveEntitlementForUpdate(
            command.userId(), command.bookIndexId(), command.occurredAt());
        if (existingEntitlement != null) {
            return new ReadingTicketUnlockResult(ReadingTicketPostResult.ALREADY_ENTITLED,
                existingEntitlement.getId(), account.getAvailableBalance());
        }
        if (account.getAvailableBalance() < 1) {
            throw new InsufficientReadingTicketException();
        }

        String idempotencyKey = "READING_TICKET_UNLOCK:" + command.userId() + ':'
            + command.bookIndexId() + ':' + command.clientRequestId();
        ReadingTicketLedgerRow replay = mapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (replay != null) {
            ChapterEntitlementRow entitlement = mapper.selectActiveEntitlement(
                command.userId(), command.bookIndexId(), command.occurredAt());
            if (entitlement == null) {
                throw new IllegalStateException("Bút toán Vé đọc không có entitlement tương ứng");
            }
            return new ReadingTicketUnlockResult(ReadingTicketPostResult.ALREADY_POSTED,
                entitlement.getId(), account.getAvailableBalance());
        }

        long balanceAfter = account.getAvailableBalance() - 1;
        String requestHash = sha256("SPEND|" + command.userId() + '|' + command.bookId() + '|'
            + command.bookIndexId() + '|' + command.clientRequestId() + '|' + command.policyVersion());
        ReadingTicketLedgerWrite write = ReadingTicketLedgerWrite.builder()
            .entryNo(entryNo()).userId(command.userId()).entryType("SPEND").amount(-1)
            .balanceAfter(balanceAfter).businessType("CHAPTER_UNLOCK")
            .businessId(Long.toString(command.bookIndexId())).idempotencyKey(idempotencyKey)
            .requestHash(requestHash).operatorType("USER").operatorId(command.userId())
            .policyVersion(command.policyVersion()).build();
        if (mapper.insertLedger(write) != 1) {
            throw new IllegalStateException("Không thể ghi bút toán dùng Vé đọc");
        }
        ReadingTicketLedgerRow ledger = requireLedger(idempotencyKey);
        consumeOneTicket(command, ledger);
        if (mapper.debitAccount(account.getId(), account.getVersion(), 1) != 1) {
            throw new IllegalStateException("Không thể trừ projection Vé đọc");
        }
        if (mapper.insertChapterEntitlement(command.userId(), command.bookId(), command.bookIndexId(),
            "READING_TICKET", Long.toString(ledger.getId()), command.occurredAt(), null,
            idempotencyKey, command.policyVersion()) != 1) {
            throw new IllegalStateException("Không thể tạo entitlement chương");
        }
        ChapterEntitlementRow entitlement = mapper.selectActiveEntitlement(
            command.userId(), command.bookIndexId(), command.occurredAt());
        if (entitlement == null) {
            throw new IllegalStateException("Không đọc được entitlement chương vừa tạo");
        }
        return new ReadingTicketUnlockResult(ReadingTicketPostResult.POSTED,
            entitlement.getId(), balanceAfter);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActiveChapterEntitlement(long userId, long bookIndexId, Date at) {
        return userId > 0 && bookIndexId > 0 && at != null
            && mapper.selectActiveEntitlement(userId, bookIndexId, at) != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingTicketExpiryResult expireDueLots(long userId, Date cutoff, String policyVersion,
                                                   int maxLotsPerUser) {
        if (userId <= 0 || cutoff == null || policyVersion == null || policyVersion.isBlank()
            || policyVersion.length() > 32 || maxLotsPerUser <= 0 || maxLotsPerUser > 10_000) {
            throw new IllegalArgumentException("Yêu cầu đóng lot Vé đọc hết hạn không hợp lệ");
        }
        ReadingTicketAccountRow account = mapper.lockAccountByUserId(userId);
        if (account == null) {
            throw new IllegalStateException("Lot Vé đọc không có tài khoản projection");
        }
        requireActive(account);
        List<ReadingTicketLotRow> lots = mapper.lockExpiredLotsByUser(userId, cutoff, maxLotsPerUser);
        if (lots.isEmpty()) {
            return ReadingTicketExpiryResult.EMPTY;
        }
        long total = 0;
        StringBuilder fingerprintInput = new StringBuilder();
        for (ReadingTicketLotRow lot : lots) {
            total = Math.addExact(total, lot.getRemainingAmount());
            if (!fingerprintInput.isEmpty()) {
                fingerprintInput.append(',');
            }
            fingerprintInput.append(lot.getId()).append(':').append(lot.getVersion())
                .append(':').append(lot.getRemainingAmount());
        }
        if (account.getAvailableBalance() < total) {
            throw new IllegalStateException("Số dư projection Vé đọc thấp hơn tổng lot cần đóng");
        }
        String fingerprint = sha256(fingerprintInput.toString()).substring(0, 24);
        String idempotencyKey = "READING_TICKET_EXPIRE:" + userId + ':' + fingerprint;
        long balanceAfter = account.getAvailableBalance() - total;
        ReadingTicketLedgerWrite write = ReadingTicketLedgerWrite.builder()
            .entryNo(entryNo()).userId(userId).entryType("EXPIRE").amount(-total)
            .balanceAfter(balanceAfter).businessType("LOT_EXPIRY").businessId(fingerprint)
            .idempotencyKey(idempotencyKey)
            .requestHash(sha256("EXPIRE|" + userId + '|' + total + '|' + fingerprintInput
                + '|' + policyVersion))
            .operatorType("SYSTEM").reason("Đóng lot Vé đọc hết hạn")
            .policyVersion(policyVersion).build();
        if (mapper.insertLedger(write) != 1) {
            throw new IllegalStateException("Không thể ghi bút toán Vé đọc hết hạn");
        }
        ReadingTicketLedgerRow ledger = requireLedger(idempotencyKey);
        for (ReadingTicketLotRow lot : lots) {
            if (mapper.expireLot(lot.getId(), lot.getVersion(), lot.getRemainingAmount(), cutoff) != 1
                || mapper.insertLotAllocation(ledger.getId(), lot.getId(), lot.getRemainingAmount(), 0) != 1) {
                throw new IllegalStateException("Lot Vé đọc hết hạn đã được cập nhật đồng thời");
            }
        }
        if (mapper.expireFromAccount(account.getId(), account.getVersion(), total) != 1) {
            throw new IllegalStateException("Không thể cập nhật projection khi Vé đọc hết hạn");
        }
        return new ReadingTicketExpiryResult(lots.size(), total);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReadingTicketAccountRow getOrCreateAccount(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Người dùng Vé đọc không hợp lệ");
        }
        ReadingTicketAccountRow account = mapper.selectAccount(userId);
        if (account == null) {
            mapper.insertAccountIgnore(userId);
            account = mapper.selectAccount(userId);
        }
        if (account == null) {
            throw new IllegalStateException("Không thể tạo tài khoản Vé đọc");
        }
        return account;
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingTicketLedgerPage listLedgerHistory(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_HISTORY_PAGE_SIZE));
        long offset = Math.multiplyExact((long) safePage - 1, safePageSize);
        long total = mapper.countLedgerByUser(userId);
        return new ReadingTicketLedgerPage(mapper.selectLedgerByUser(userId, offset, safePageSize),
            total, safePage, safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ReadingTicketLotPage listLotHistory(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_HISTORY_PAGE_SIZE));
        long offset = Math.multiplyExact((long) safePage - 1, safePageSize);
        long total = mapper.countLotsByUser(userId);
        return new ReadingTicketLotPage(mapper.selectLotsByUser(userId, offset, safePageSize),
            total, safePage, safePageSize);
    }

    private void consumeOneTicket(ReadingTicketUnlockCommand command, ReadingTicketLedgerRow ledger) {
        List<ReadingTicketLotRow> lots = mapper.lockSpendableLots(
            command.userId(), command.occurredAt(), command.maxLotsPerSpend());
        for (ReadingTicketLotRow lot : lots) {
            if (lot.getRemainingAmount() == null || lot.getRemainingAmount() <= 0) {
                continue;
            }
            long remainingAfter = lot.getRemainingAmount() - 1;
            if (mapper.consumeLot(lot.getId(), lot.getVersion(), 1, command.occurredAt()) != 1
                || mapper.insertLotAllocation(ledger.getId(), lot.getId(), 1, remainingAfter) != 1) {
                throw new IllegalStateException("Lô Vé đọc đã được cập nhật đồng thời");
            }
            return;
        }
        throw new IllegalStateException("Số dư Vé đọc không khớp với các lô còn hiệu lực");
    }

    private ReadingTicketUnlockResult existingEntitlementResult(long userId,
                                                                 ChapterEntitlementRow entitlement) {
        ReadingTicketAccountRow account = mapper.selectAccount(userId);
        long balance = account == null ? 0 : account.getAvailableBalance();
        return new ReadingTicketUnlockResult(ReadingTicketPostResult.ALREADY_ENTITLED,
            entitlement.getId(), balance);
    }

    private ReadingTicketAccountRow lockAccount(long userId) {
        ReadingTicketAccountRow account = mapper.lockAccountByUserId(userId);
        if (account == null) {
            mapper.insertAccountIgnore(userId);
            account = mapper.lockAccountByUserId(userId);
        }
        if (account == null) {
            throw new IllegalStateException("Không thể khóa tài khoản Vé đọc");
        }
        return account;
    }

    private void requireActive(ReadingTicketAccountRow account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException("Tài khoản Vé đọc đang bị đóng băng");
        }
    }

    private ReadingTicketLedgerRow requireLedger(String idempotencyKey) {
        ReadingTicketLedgerRow ledger = mapper.selectLedgerByIdempotencyKey(idempotencyKey);
        if (ledger == null) {
            throw new IllegalStateException("Không đọc được bút toán Vé đọc vừa tạo");
        }
        return ledger;
    }

    private void validateExistingGrant(ReadingTicketLedgerRow ledger,
                                       ReadingTicketGrantCommand command, String requestHash) {
        if (!Objects.equals(ledger.getUserId(), command.userId())
            || !Objects.equals(ledger.getAmount(), command.amount())
            || !Objects.equals(ledger.getBusinessType(), command.sourceType())
            || !Objects.equals(ledger.getBusinessId(), command.sourceRef())
            || !Objects.equals(ledger.getRequestHash(), requestHash)
            || !Objects.equals(ledger.getPolicyVersion(), command.policyVersion())
            || !"GRANT".equals(ledger.getEntryType())) {
            throw new IllegalStateException("Khóa idempotency Vé đọc đã dùng cho nội dung khác");
        }
    }

    private String grantRequestHash(ReadingTicketGrantCommand command) {
        return sha256("GRANT|" + command.userId() + '|' + command.amount() + '|'
            + command.sourceType() + '|' + command.sourceRef() + '|' + command.effectiveAt().getTime()
            + '|' + command.expireAt().getTime() + '|' + command.operatorType() + '|'
            + Objects.toString(command.operatorId(), "") + '|' + Objects.toString(command.reason(), "")
            + '|' + command.policyVersion());
    }

    private String entryNo() {
        return "RT-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }
}
