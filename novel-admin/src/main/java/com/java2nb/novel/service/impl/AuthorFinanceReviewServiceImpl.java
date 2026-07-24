package com.java2nb.novel.service.impl;

import com.java2nb.common.security.AdminPiiCryptoService;
import com.java2nb.novel.dao.AuthorFinanceReviewDao;
import com.java2nb.novel.domain.AuthorKycReviewDO;
import com.java2nb.novel.domain.AuthorWithdrawalReviewDO;
import com.java2nb.novel.service.AuthorFinanceReviewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class AuthorFinanceReviewServiceImpl implements AuthorFinanceReviewService {

    private final AuthorFinanceReviewDao dao;
    private final AdminPiiCryptoService piiCryptoService;

    public AuthorFinanceReviewServiceImpl(AuthorFinanceReviewDao dao, AdminPiiCryptoService piiCryptoService) {
        this.dao = dao;
        this.piiCryptoService = piiCryptoService;
    }

    @Override
    public List<AuthorKycReviewDO> listKyc(Map<String, Object> params) {
        return dao.listKyc(params);
    }

    @Override
    public int countKyc(Map<String, Object> params) {
        return dao.countKyc(params);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorKycReviewDO getKycDetail(long id, long actorId) {
        AuthorKycReviewDO profile = requireKyc(id);
        profile.setLegalName(piiCryptoService.decrypt(profile.getLegalNameCiphertext()));
        profile.setDateOfBirth(piiCryptoService.decrypt(profile.getDateOfBirthCiphertext()));
        profile.setIdentityNumber(piiCryptoService.decrypt(profile.getIdentityNumberCiphertext()));
        profile.setTaxCode(piiCryptoService.decrypt(profile.getTaxCodeCiphertext()));
        profile.setBankAccount(piiCryptoService.decrypt(profile.getBankAccountCiphertext()));
        profile.setBankAccountName(piiCryptoService.decrypt(profile.getBankAccountNameCiphertext()));
        if (dao.insertKycAudit(profile, "VIEWED_PII", profile.getStatus(), actorId,
            "Quản trị viên xem hồ sơ KYC") != 1) {
            throw new IllegalStateException("Không thể ghi audit truy cập KYC");
        }
        return profile;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void approveKyc(long id, int expectedVersion, long actorId) {
        if (!piiCryptoService.isConfigured()) {
            throw new IllegalStateException("Chưa cấu hình khóa giải mã KYC");
        }
        reviewKyc(id, expectedVersion, "VERIFIED", null, actorId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rejectKyc(long id, int expectedVersion, String reason, long actorId) {
        reviewKyc(id, expectedVersion, "REJECTED", requireReason(reason), actorId);
    }

    private void reviewKyc(long id, int expectedVersion, String toStatus, String reason, long actorId) {
        AuthorKycReviewDO profile = requireKyc(id);
        if (!"PENDING".equals(profile.getStatus()) || profile.getSubmissionVersion() != expectedVersion) {
            throw new IllegalStateException("Hồ sơ KYC không còn ở phiên bản chờ duyệt");
        }
        if (dao.reviewKyc(id, expectedVersion, toStatus, actorId, reason) != 1) {
            throw new IllegalStateException("Hồ sơ KYC đã được xử lý đồng thời");
        }
        if (dao.insertKycAudit(profile, "VERIFIED".equals(toStatus) ? "REVIEW_APPROVED" : "REVIEW_REJECTED",
            toStatus, actorId, reason) != 1) {
            throw new IllegalStateException("Không thể ghi audit duyệt KYC");
        }
    }

    @Override
    public List<AuthorWithdrawalReviewDO> listWithdrawals(Map<String, Object> params) {
        return dao.listWithdrawals(params);
    }

    @Override
    public int countWithdrawals(Map<String, Object> params) {
        return dao.countWithdrawals(params);
    }

    @Override
    public AuthorWithdrawalReviewDO getWithdrawal(long id) {
        return requireWithdrawal(id);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorWithdrawalReviewDO getWithdrawalPayoutDetail(long id, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        withdrawal.setBankAccount(piiCryptoService.decrypt(withdrawal.getBankAccountCiphertext()));
        withdrawal.setBankAccountName(piiCryptoService.decrypt(withdrawal.getBankAccountNameCiphertext()));
        if (dao.insertWithdrawalAudit(withdrawal, "VIEWED_PAYOUT_PII", withdrawal.getStatus(), actorId,
            "Quản trị viên xem snapshot tài khoản nhận tiền") != 1) {
            throw new IllegalStateException("Không thể ghi audit truy cập tài khoản nhận tiền");
        }
        return withdrawal;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void approveWithdrawal(long id, long expectedVersion, long withheldTaxVnd, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        requireState(withdrawal, "PENDING_REVIEW", expectedVersion);
        if (withheldTaxVnd < 0 || withheldTaxVnd > withdrawal.getGrossAmountVnd()) {
            throw new IllegalArgumentException("Số thuế khấu trừ không hợp lệ");
        }
        long netAmount = withdrawal.getGrossAmountVnd() - withheldTaxVnd;
        if (dao.approveWithdrawal(id, expectedVersion, withheldTaxVnd, netAmount, actorId) != 1) {
            throw new IllegalStateException("Yêu cầu rút đã được xử lý đồng thời");
        }
        auditWithdrawal(withdrawal, "APPROVED", "APPROVED", actorId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void rejectWithdrawal(long id, long expectedVersion, String reason, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        if (!"PENDING_REVIEW".equals(withdrawal.getStatus()) && !"APPROVED".equals(withdrawal.getStatus())) {
            throw new IllegalStateException("Chỉ được từ chối yêu cầu chưa chuyển khoản");
        }
        requestRelease(withdrawal, expectedVersion, "REJECTED", requireReason(reason), actorId,
            "REJECTION_REQUESTED");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markProcessing(long id, long expectedVersion, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        requireState(withdrawal, "APPROVED", expectedVersion);
        if (dao.markWithdrawalProcessing(id, expectedVersion, actorId) != 1) {
            throw new IllegalStateException("Yêu cầu rút đã được xử lý đồng thời");
        }
        auditWithdrawal(withdrawal, "PROCESSING_STARTED", "PROCESSING", actorId, null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markPaid(long id, long expectedVersion, String providerReference, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        requireState(withdrawal, "PROCESSING", expectedVersion);
        String reference = providerReference == null ? "" : providerReference.trim();
        if (reference.length() < 3 || reference.length() > 128) {
            throw new IllegalArgumentException("Mã tham chiếu chuyển khoản không hợp lệ");
        }
        if (dao.requestWithdrawalSettlement(id, expectedVersion, actorId, reference) != 1) {
            throw new IllegalStateException("Yêu cầu rút đã được xử lý đồng thời");
        }
        auditWithdrawal(withdrawal, "PAYMENT_RECORDED", "SETTLEMENT_PENDING", actorId, reference);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markFailed(long id, long expectedVersion, String reason, long actorId) {
        AuthorWithdrawalReviewDO withdrawal = requireWithdrawal(id);
        requireState(withdrawal, "PROCESSING", expectedVersion);
        requestRelease(withdrawal, expectedVersion, "FAILED", requireReason(reason), actorId,
            "PAYMENT_FAILED");
    }

    private void requestRelease(AuthorWithdrawalReviewDO withdrawal, long expectedVersion, String targetStatus,
                                String reason, long actorId, String eventType) {
        requireState(withdrawal, withdrawal.getStatus(), expectedVersion);
        if (dao.requestWithdrawalRelease(withdrawal.getId(), withdrawal.getStatus(), expectedVersion, targetStatus,
            actorId, reason) != 1) {
            throw new IllegalStateException("Yêu cầu rút đã được xử lý đồng thời");
        }
        auditWithdrawal(withdrawal, eventType, "RELEASE_PENDING", actorId, reason);
    }

    private void auditWithdrawal(AuthorWithdrawalReviewDO withdrawal, String eventType, String toStatus,
                                 long actorId, String reason) {
        if (dao.insertWithdrawalAudit(withdrawal, eventType, toStatus, actorId, reason) != 1) {
            throw new IllegalStateException("Không thể ghi audit yêu cầu rút");
        }
    }

    private AuthorKycReviewDO requireKyc(long id) {
        AuthorKycReviewDO profile = dao.getKyc(id);
        if (profile == null) {
            throw new IllegalArgumentException("Không tìm thấy hồ sơ KYC");
        }
        return profile;
    }

    private AuthorWithdrawalReviewDO requireWithdrawal(long id) {
        AuthorWithdrawalReviewDO withdrawal = dao.getWithdrawal(id);
        if (withdrawal == null) {
            throw new IllegalArgumentException("Không tìm thấy yêu cầu rút");
        }
        return withdrawal;
    }

    private void requireState(AuthorWithdrawalReviewDO withdrawal, String status, long expectedVersion) {
        if (!status.equals(withdrawal.getStatus()) || withdrawal.getVersion() != expectedVersion) {
            throw new IllegalStateException("Trạng thái hoặc phiên bản yêu cầu rút đã thay đổi");
        }
    }

    private String requireReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new IllegalArgumentException("Lý do phải dài từ 10 đến 500 ký tự");
        }
        return normalized;
    }
}
