package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletAccountRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletHistoryPage;
import com.java2nb.novel.service.wallet.WalletEntryRow;
import com.java2nb.novel.service.wallet.WalletPostResult;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletLedgerServiceImpl implements WalletLedgerService {

    private static final WalletRef SYSTEM_ISSUANCE = new WalletRef("SYSTEM", 0L, "SYSTEM_ISSUANCE");
    private static final WalletRef PLATFORM_REVENUE = new WalletRef("SYSTEM", 0L, "PLATFORM_REVENUE");
    private static final WalletRef PAYOUT_CLEARING = new WalletRef("SYSTEM", 0L, "PAYOUT_CLEARING");

    private final WalletLedgerMapper walletLedgerMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult creditReaderTopUp(long userId, long amount, String businessId, String idempotencyKey) {
        requirePositive(amount, "Số Xu nạp phải lớn hơn 0");
        WalletRef reader = readerWallet(userId);
        Map<WalletRef, Long> entries = new LinkedHashMap<>();
        entries.put(SYSTEM_ISSUANCE, -amount);
        entries.put(reader, amount);
        return post("TOP_UP", businessId, amount, idempotencyKey, "Nạp Xu qua cổng thanh toán", entries, userId,
            null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult purchaseChapter(long userId, long authorId, long amount, long authorAmount,
                                            String businessId, String idempotencyKey) {
        requirePositive(amount, "Giá chương phải lớn hơn 0");
        if (authorAmount < 0 || authorAmount > amount) {
            throw new IllegalArgumentException("Phần doanh thu tác giả không hợp lệ");
        }
        long platformAmount = amount - authorAmount;
        Map<WalletRef, Long> entries = new LinkedHashMap<>();
        entries.put(readerWallet(userId), -amount);
        entries.put(authorWallet(authorId), authorAmount);
        entries.put(PLATFORM_REVENUE, platformAmount);
        return post("CHAPTER_PURCHASE", businessId, amount, idempotencyKey, "Mua chương truyện", entries, userId,
            null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult creditReaderReward(long userId, long amount, String businessId, String idempotencyKey,
                                               String description) {
        requirePositive(amount, "Số Xu thưởng phải lớn hơn 0");
        Map<WalletRef, Long> entries = new LinkedHashMap<>();
        entries.put(SYSTEM_ISSUANCE, -amount);
        entries.put(readerWallet(userId), amount);
        return post("REWARD", businessId, amount, idempotencyKey, description, entries, userId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult reverseTransaction(String originalIdempotencyKey, String businessType,
                                               String businessId, String idempotencyKey, String description) {
        requireText(originalIdempotencyKey, "Thiếu khóa giao dịch gốc");
        requireText(businessType, "Thiếu loại giao dịch đảo");
        LedgerTransactionRow original = walletLedgerMapper
            .selectTransactionByIdempotencyKey(originalIdempotencyKey);
        if (original == null) {
            throw new IllegalArgumentException("Không tìm thấy giao dịch gốc");
        }
        if (original.getReversalOfTransactionId() != null) {
            throw new IllegalArgumentException("Không được đảo một giao dịch đảo");
        }
        List<WalletEntryRow> originalEntries = walletLedgerMapper.selectEntriesByTransactionId(original.getId());
        if (originalEntries.size() < 2) {
            throw new IllegalStateException("Giao dịch gốc không có đủ bút toán cân bằng");
        }
        Map<WalletRef, Long> reversedEntries = new LinkedHashMap<>();
        Long readerUserId = null;
        for (WalletEntryRow entry : originalEntries) {
            WalletRef ref = new WalletRef(entry.getOwnerType(), entry.getOwnerId(), entry.getAccountType());
            reversedEntries.put(ref, Math.negateExact(entry.getAmount()));
            if ("USER".equals(entry.getOwnerType()) && "READER_XU".equals(entry.getAccountType())) {
                if (readerUserId != null && !readerUserId.equals(entry.getOwnerId())) {
                    throw new IllegalStateException("Giao dịch gốc chứa nhiều ví độc giả");
                }
                readerUserId = entry.getOwnerId();
            }
        }
        return post(businessType, businessId, original.getTotalAmount(), idempotencyKey, description,
            reversedEntries, readerUserId, original.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult holdAuthorWithdrawal(long authorId, long amount, String withdrawalNo,
                                                 String idempotencyKey) {
        requirePositive(amount, "Số Xu yêu cầu rút phải lớn hơn 0");
        Map<WalletRef, Long> entries = new LinkedHashMap<>();
        entries.put(authorWallet(authorId), -amount);
        entries.put(PAYOUT_CLEARING, amount);
        return post("AUTHOR_WITHDRAWAL_HOLD", withdrawalNo, amount, idempotencyKey,
            "Giữ Xu chờ thanh toán thu nhập tác giả", entries, null, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public WalletPostResult settleAuthorWithdrawal(long amount, String withdrawalNo, String idempotencyKey) {
        requirePositive(amount, "Số Xu tất toán phải lớn hơn 0");
        Map<WalletRef, Long> entries = new LinkedHashMap<>();
        entries.put(PAYOUT_CLEARING, -amount);
        entries.put(SYSTEM_ISSUANCE, amount);
        return post("AUTHOR_WITHDRAWAL_SETTLED", withdrawalNo, amount, idempotencyKey,
            "Tất toán Xu sau khi đã chuyển khoản tác giả", entries, null, null);
    }

    @Override
    public long getAuthorAvailableBalance(long authorId) {
        Long balance = walletLedgerMapper.selectAuthorAvailableBalance(authorId);
        return balance == null ? 0L : balance;
    }

    @Override
    public WalletHistoryPage listReaderHistory(long userId, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, 100));
        long offset = Math.multiplyExact((long) safePage - 1, safePageSize);
        long total = walletLedgerMapper.countReaderHistory(userId);
        return new WalletHistoryPage(walletLedgerMapper.selectReaderHistory(userId, offset, safePageSize), total,
            safePage, safePageSize);
    }

    private WalletPostResult post(String businessType, String businessId, long totalAmount, String idempotencyKey,
                                  String description, Map<WalletRef, Long> requestedEntries, Long readerUserId,
                                  Long reversalOfTransactionId) {
        requireText(businessId, "Thiếu mã nghiệp vụ sổ cái");
        requireText(idempotencyKey, "Thiếu khóa idempotency sổ cái");
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Khóa idempotency vượt quá 128 ký tự");
        }

        long entryTotal = requestedEntries.values().stream().reduce(0L, Math::addExact);
        if (entryTotal != 0) {
            throw new IllegalArgumentException("Bút toán sổ cái không cân bằng");
        }
        String requestHash = requestHash(businessType, businessId, totalAmount, reversalOfTransactionId,
            requestedEntries);
        LedgerTransactionRow existing = walletLedgerMapper.selectTransactionByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            validateExisting(existing, businessType, businessId, totalAmount, reversalOfTransactionId, requestHash);
            return WalletPostResult.ALREADY_POSTED;
        }

        try {
            walletLedgerMapper.insertTransaction("TX-" + UUID.randomUUID().toString().replace("-", ""),
                idempotencyKey, requestHash, businessType, businessId, totalAmount, reversalOfTransactionId,
                description);
        } catch (DuplicateKeyException exception) {
            existing = walletLedgerMapper.selectTransactionByIdempotencyKey(idempotencyKey);
            if (existing == null) {
                throw exception;
            }
            validateExisting(existing, businessType, businessId, totalAmount, reversalOfTransactionId, requestHash);
            return WalletPostResult.ALREADY_POSTED;
        }

        LedgerTransactionRow transaction = walletLedgerMapper.selectTransactionByIdempotencyKey(idempotencyKey);
        if (transaction == null) {
            throw new IllegalStateException("Không đọc được giao dịch sổ cái vừa tạo");
        }

        Map<WalletRef, Long> walletIds = ensureWallets(requestedEntries.keySet());
        List<Long> sortedWalletIds = walletIds.values().stream().sorted().toList();
        List<WalletAccountRow> lockedWallets = walletLedgerMapper.lockWalletAccounts(sortedWalletIds);
        if (lockedWallets.size() != sortedWalletIds.size()) {
            throw new IllegalStateException("Không khóa được đầy đủ tài khoản ví");
        }

        Map<Long, WalletRef> refsById = new LinkedHashMap<>();
        walletIds.forEach((ref, id) -> refsById.put(id, ref));
        Long readerBalanceAfter = null;
        for (WalletAccountRow wallet : lockedWallets) {
            WalletRef ref = refsById.get(wallet.getId());
            long amount = requestedEntries.getOrDefault(ref, 0L);
            if (amount == 0) {
                continue;
            }
            long balanceAfter = Math.addExact(wallet.getAvailableBalance(), amount);
            if (!"SYSTEM".equals(ref.ownerType()) && balanceAfter < 0) {
                throw new InsufficientWalletBalanceException();
            }
            if (walletLedgerMapper.updateWalletBalance(wallet.getId(), wallet.getVersion(), amount) != 1) {
                throw new IllegalStateException("Ví đã được cập nhật đồng thời");
            }
            if (walletLedgerMapper.insertEntry(transaction.getId(), wallet.getId(), amount, balanceAfter) != 1) {
                throw new IllegalStateException("Không thể ghi bút toán ví");
            }
            if (readerUserId != null && ref.equals(readerWallet(readerUserId))) {
                readerBalanceAfter = balanceAfter;
            }
        }

        if (readerUserId != null) {
            if (readerBalanceAfter == null
                || walletLedgerMapper.syncUserBalance(readerUserId, readerBalanceAfter) != 1) {
                throw new IllegalStateException("Không thể đồng bộ số dư ví độc giả");
            }
        }
        return WalletPostResult.POSTED;
    }

    private Map<WalletRef, Long> ensureWallets(Iterable<WalletRef> refs) {
        List<WalletRef> sortedRefs = new ArrayList<>();
        refs.forEach(sortedRefs::add);
        sortedRefs.sort(Comparator.comparing(WalletRef::ownerType)
            .thenComparingLong(WalletRef::ownerId)
            .thenComparing(WalletRef::accountType));

        Map<WalletRef, Long> walletIds = new LinkedHashMap<>();
        for (WalletRef ref : sortedRefs) {
            walletLedgerMapper.insertWalletAccount(ref.ownerType(), ref.ownerId(), ref.accountType());
            WalletAccountRow wallet = walletLedgerMapper.selectWalletAccount(ref.ownerType(), ref.ownerId(),
                ref.accountType());
            if (wallet == null) {
                throw new IllegalStateException("Không thể tạo hoặc đọc tài khoản ví");
            }
            walletIds.put(ref, wallet.getId());
        }
        return walletIds;
    }

    private void validateExisting(LedgerTransactionRow existing, String businessType, String businessId,
                                  long totalAmount, Long reversalOfTransactionId, String requestHash) {
        if (!Objects.equals(existing.getRequestHash(), requestHash)
            || !Objects.equals(existing.getBusinessType(), businessType)
            || !Objects.equals(existing.getBusinessId(), businessId)
            || !Objects.equals(existing.getTotalAmount(), totalAmount)
            || !Objects.equals(existing.getReversalOfTransactionId(), reversalOfTransactionId)) {
            throw new IllegalStateException("Khóa idempotency đã được dùng cho nội dung giao dịch khác");
        }
    }

    private String requestHash(String businessType, String businessId, long totalAmount,
                               Long reversalOfTransactionId,
                               Map<WalletRef, Long> entries) {
        StringBuilder canonical = new StringBuilder()
            .append(businessType).append('|').append(businessId).append('|').append(totalAmount)
            .append('|').append(reversalOfTransactionId == null ? "" : reversalOfTransactionId);
        entries.entrySet().stream()
            .sorted(Map.Entry.comparingByKey(Comparator.comparing(WalletRef::ownerType)
                .thenComparingLong(WalletRef::ownerId)
                .thenComparing(WalletRef::accountType)))
            .forEach(entry -> canonical.append('|').append(entry.getKey().ownerType())
                .append(':').append(entry.getKey().ownerId())
                .append(':').append(entry.getKey().accountType())
                .append(':').append(entry.getValue()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }

    private WalletRef readerWallet(long userId) {
        return new WalletRef("USER", userId, "READER_XU");
    }

    private WalletRef authorWallet(long authorId) {
        return new WalletRef("AUTHOR", authorId, "AUTHOR_REVENUE_XU");
    }

    private void requirePositive(long value, String message) {
        if (value <= 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private record WalletRef(String ownerType, long ownerId, String accountType) {
    }
}
