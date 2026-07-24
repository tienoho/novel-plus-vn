package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.config.AuthorPayoutProperties;
import com.java2nb.novel.mapper.AuthorFinanceMapper;
import com.java2nb.novel.service.finance.AuthorFinanceService;
import com.java2nb.novel.service.finance.AuthorKycRow;
import com.java2nb.novel.service.finance.AuthorKycStatus;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import com.java2nb.novel.service.finance.KycSubmissionRequest;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.finance.PiiEncryptionUnavailableException;
import com.java2nb.novel.service.finance.WithdrawalRequestInput;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorFinanceServiceImpl implements AuthorFinanceService {

    static final String CURRENT_CONSENT_VERSION = "KYC-VN-2026-01";
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final AuthorFinanceMapper authorFinanceMapper;
    private final WalletLedgerService walletLedgerService;
    private final PiiCryptoService piiCryptoService;
    private final AuthorPayoutProperties payoutProperties;
    private final Clock clock = Clock.system(VIETNAM_ZONE);

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorKycStatus submitKyc(long authorId, long userId, KycSubmissionRequest request) {
        if (!piiCryptoService.isConfigured()) {
            throw new PiiEncryptionUnavailableException();
        }
        ValidatedKyc validated = validateKyc(request);
        AuthorKycRow existing = authorFinanceMapper.selectKycByAuthorId(authorId);
        if (existing != null && !Objects.equals(existing.getUserId(), userId)) {
            throw new IllegalStateException("Hồ sơ KYC không thuộc người dùng hiện tại");
        }
        if (existing != null && ("PENDING".equals(existing.getStatus()) || "VERIFIED".equals(existing.getStatus()))) {
            throw new IllegalStateException("Hồ sơ KYC đang chờ duyệt hoặc đã được xác minh");
        }

        Date now = Date.from(clock.instant());
        AuthorKycRow row = encryptedRow(authorId, userId, validated, now);
        String fromStatus = null;
        try {
            if (existing == null) {
                if (authorFinanceMapper.insertKyc(row) != 1) {
                    throw new IllegalStateException("Không thể lưu hồ sơ KYC");
                }
            } else {
                fromStatus = existing.getStatus();
                row.setId(existing.getId());
                row.setSubmissionVersion(existing.getSubmissionVersion());
                if (authorFinanceMapper.updateRejectedKyc(row) != 1) {
                    throw new IllegalStateException("Hồ sơ KYC đã được cập nhật đồng thời");
                }
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Giấy tờ định danh đã được dùng cho hồ sơ khác", exception);
        }

        AuthorKycRow saved = authorFinanceMapper.selectKycByAuthorId(authorId);
        if (saved == null) {
            throw new IllegalStateException("Không đọc được hồ sơ KYC vừa lưu");
        }
        if (authorFinanceMapper.insertKycAudit(saved.getId(), authorId, userId,
            existing == null ? "SUBMITTED" : "RESUBMITTED", fromStatus, "PENDING", "AUTHOR", userId, null) != 1) {
            throw new IllegalStateException("Không thể ghi audit KYC");
        }
        return toStatus(saved);
    }

    @Override
    public AuthorKycStatus getKycStatus(long authorId) {
        AuthorKycRow row = authorFinanceMapper.selectKycByAuthorId(authorId);
        return row == null ? new AuthorKycStatus("NOT_SUBMITTED", null, null, null, null, 0, null, null)
            : toStatus(row);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorWithdrawalRow requestWithdrawal(long authorId, long userId, WithdrawalRequestInput request) {
        if (request == null || request.getAmountXu() == null || request.getAmountXu() <= 0) {
            throw new IllegalArgumentException("Số Xu yêu cầu rút không hợp lệ");
        }
        String idempotencyKey = normalizeIdempotencyKey(request.getIdempotencyKey());
        AuthorWithdrawalRow existing = authorFinanceMapper.selectWithdrawalByIdempotencyKey(idempotencyKey);
        if (existing != null) {
            if (!Objects.equals(existing.getAuthorId(), authorId) || !Objects.equals(existing.getUserId(), userId)
                || !Objects.equals(existing.getRequestedXu(), request.getAmountXu())) {
                throw new IllegalStateException("Khóa idempotency đã được dùng cho yêu cầu rút khác");
            }
            return existing;
        }
        if (!payoutProperties.isConfigured() || !piiCryptoService.isConfigured()) {
            throw new IllegalStateException("Chức năng rút thu nhập chưa được cấu hình");
        }
        LocalDate today = LocalDate.now(clock);
        if (today.getDayOfMonth() < payoutProperties.getRequestOpenDay()
            || today.getDayOfMonth() > payoutProperties.getRequestCloseDay()) {
            throw new IllegalStateException("Hiện ngoài kỳ tiếp nhận yêu cầu rút thu nhập");
        }
        if (request.getAmountXu() < payoutProperties.getMinimumXu()) {
            throw new IllegalArgumentException("Số Xu thấp hơn mức rút tối thiểu");
        }

        AuthorKycRow kyc = authorFinanceMapper.selectKycByAuthorId(authorId);
        if (kyc == null || !"VERIFIED".equals(kyc.getStatus()) || !Objects.equals(kyc.getUserId(), userId)) {
            throw new IllegalStateException("Tác giả chưa hoàn tất KYC");
        }
        long available = walletLedgerService.getAuthorAvailableBalance(authorId);
        if (available < request.getAmountXu()) {
            throw new InsufficientWalletBalanceException();
        }

        String withdrawalNo = "WD-" + UUID.randomUUID().toString().replace("-", "");
        String holdKey = "AUTHOR_WITHDRAWAL_HOLD:" + withdrawalNo;
        long grossAmountVnd = Math.multiplyExact(request.getAmountXu(), payoutProperties.getVndPerXu());
        try {
            if (authorFinanceMapper.insertWithdrawal(withdrawalNo, idempotencyKey, authorId, userId, kyc.getId(),
                request.getAmountXu(), payoutProperties.getVndPerXu(), grossAmountVnd, kyc.getBankCode(),
                kyc.getBankAccountCiphertext(), kyc.getBankAccountLast4(), kyc.getBankAccountNameCiphertext(),
                holdKey) != 1) {
                throw new IllegalStateException("Không thể tạo yêu cầu rút thu nhập");
            }
        } catch (DuplicateKeyException exception) {
            AuthorWithdrawalRow concurrent = authorFinanceMapper.selectWithdrawalByIdempotencyKey(idempotencyKey);
            if (concurrent != null && Objects.equals(concurrent.getAuthorId(), authorId)
                && Objects.equals(concurrent.getRequestedXu(), request.getAmountXu())) {
                return concurrent;
            }
            throw exception;
        }

        walletLedgerService.holdAuthorWithdrawal(authorId, request.getAmountXu(), withdrawalNo, holdKey);
        AuthorWithdrawalRow saved = authorFinanceMapper.selectWithdrawalByIdempotencyKey(idempotencyKey);
        if (saved == null || authorFinanceMapper.insertWithdrawalAudit(saved.getId(), saved.getWithdrawalNo(),
            "REQUESTED", null, "PENDING_REVIEW", "AUTHOR", userId, null) != 1) {
            throw new IllegalStateException("Không thể ghi audit yêu cầu rút");
        }
        return saved;
    }

    @Override
    public long getAvailableRevenue(long authorId) {
        return walletLedgerService.getAuthorAvailableBalance(authorId);
    }

    private AuthorKycRow encryptedRow(long authorId, long userId, ValidatedKyc value, Date now) {
        AuthorKycRow row = new AuthorKycRow();
        row.setAuthorId(authorId);
        row.setUserId(userId);
        row.setLegalNameCiphertext(piiCryptoService.encrypt(value.legalName()));
        row.setDateOfBirthCiphertext(piiCryptoService.encrypt(value.dateOfBirth().toString()));
        row.setIdentityType(value.identityType());
        row.setIdentityNumberCiphertext(piiCryptoService.encrypt(value.identityNumber()));
        row.setIdentityNumberHash(piiCryptoService.deterministicHash("IDENTITY:" + value.identityNumber()));
        row.setIdentityNumberLast4(lastFour(value.identityNumber()));
        row.setTaxCodeCiphertext(piiCryptoService.encrypt(value.taxCode()));
        row.setBankCode(value.bankCode());
        row.setBankAccountCiphertext(piiCryptoService.encrypt(value.bankAccount()));
        row.setBankAccountHash(piiCryptoService.deterministicHash("BANK:" + value.bankCode() + ':'
            + value.bankAccount()));
        row.setBankAccountLast4(lastFour(value.bankAccount()));
        row.setBankAccountNameCiphertext(piiCryptoService.encrypt(value.bankAccountName()));
        row.setConsentVersion(CURRENT_CONSENT_VERSION);
        row.setConsentedAt(now);
        row.setSubmittedAt(now);
        return row;
    }

    private ValidatedKyc validateKyc(KycSubmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu hồ sơ KYC");
        }
        String legalName = normalizeName(request.getLegalName(), "Họ tên pháp lý");
        String bankAccountName = normalizeName(request.getBankAccountName(), "Tên chủ tài khoản");
        if (request.getDateOfBirth() == null
            || ChronoUnit.YEARS.between(request.getDateOfBirth(), LocalDate.now(clock)) < 18) {
            throw new IllegalArgumentException("Tác giả phải đủ 18 tuổi để gửi KYC");
        }
        String identityType = normalizeUpper(request.getIdentityType());
        if (!"CCCD".equals(identityType) && !"PASSPORT".equals(identityType)) {
            throw new IllegalArgumentException("Loại giấy tờ định danh không hợp lệ");
        }
        String identityNumber = normalizeUpper(request.getIdentityNumber()).replaceAll("[\\s-]", "");
        if (("CCCD".equals(identityType) && !identityNumber.matches("\\d{12}"))
            || ("PASSPORT".equals(identityType) && !identityNumber.matches("[A-Z0-9]{6,12}"))) {
            throw new IllegalArgumentException("Số giấy tờ định danh không hợp lệ");
        }
        String taxCode = request.getTaxCode() == null ? null : request.getTaxCode().replaceAll("[\\s-]", "");
        if (taxCode != null && !taxCode.isBlank() && !taxCode.matches("\\d{10}(?:\\d{3})?")) {
            throw new IllegalArgumentException("Mã số thuế không hợp lệ");
        }
        String bankCode = normalizeUpper(request.getBankCode());
        if (!bankCode.matches("[A-Z0-9_-]{2,20}")) {
            throw new IllegalArgumentException("Mã ngân hàng không hợp lệ");
        }
        String bankAccount = request.getBankAccount() == null ? ""
            : request.getBankAccount().replaceAll("\\s", "");
        if (!bankAccount.matches("\\d{6,20}")) {
            throw new IllegalArgumentException("Số tài khoản ngân hàng không hợp lệ");
        }
        if (!CURRENT_CONSENT_VERSION.equals(request.getConsentVersion())) {
            throw new IllegalArgumentException("Chưa chấp thuận phiên bản điều khoản KYC hiện hành");
        }
        return new ValidatedKyc(legalName, request.getDateOfBirth(), identityType, identityNumber,
            taxCode == null || taxCode.isBlank() ? null : taxCode, bankCode, bankAccount, bankAccountName);
    }

    private AuthorKycStatus toStatus(AuthorKycRow row) {
        return new AuthorKycStatus(row.getStatus(), row.getIdentityType(), "****" + row.getIdentityNumberLast4(),
            row.getBankCode(), "****" + row.getBankAccountLast4(), row.getSubmissionVersion(), row.getSubmittedAt(),
            row.getRejectionReason());
    }

    private String normalizeName(String value, String fieldName) {
        String normalized = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (normalized.length() < 2 || normalized.length() > 100
            || !normalized.matches("[\\p{L} .'-]+")) {
            throw new IllegalArgumentException(fieldName + " không hợp lệ");
        }
        return normalized;
    }

    private String normalizeUpper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeIdempotencyKey(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() < 8 || normalized.length() > 128
            || !normalized.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Khóa idempotency yêu cầu rút không hợp lệ");
        }
        return normalized;
    }

    private String lastFour(String value) {
        return value.substring(value.length() - 4);
    }

    private record ValidatedKyc(String legalName, LocalDate dateOfBirth, String identityType,
                                String identityNumber, String taxCode, String bankCode,
                                String bankAccount, String bankAccountName) {
    }
}
