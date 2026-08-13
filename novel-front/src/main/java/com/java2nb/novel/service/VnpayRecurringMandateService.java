package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

@Service
public class VnpayRecurringMandateService {

    private static final DateTimeFormatter TOKEN_EXPIRY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VnpayRecurringProperties properties;
    private final VnpayRecurringSigner signer;
    private final VnpayRecurringClient client;
    private final ReadingSubscriptionMapper subscriptionMapper;
    private final ReadingSubscriptionMandateMapper mandateMapper;
    private final PiiCryptoService piiCryptoService;

    public VnpayRecurringMandateService(VnpayRecurringProperties properties,
                                        VnpayRecurringSigner signer,
                                        VnpayRecurringClient client,
                                        ReadingSubscriptionMapper subscriptionMapper,
                                        ReadingSubscriptionMandateMapper mandateMapper,
                                        PiiCryptoService piiCryptoService) {
        this.properties = properties;
        this.signer = signer;
        this.client = client;
        this.subscriptionMapper = subscriptionMapper;
        this.mandateMapper = mandateMapper;
        this.piiCryptoService = piiCryptoService;
    }

    public VnpayRecurringMandateInitialization create(long userId, String planCode,
                                                       long acceptedPlanVersion,
                                                       String clientRequestId,
                                                       String ipAddress, String userAgent) {
        if (!properties.isConfigured() || !piiCryptoService.isConfigured()) {
            throw new VnpayRecurringUnavailableException("VNPAY Recurring chưa được cấu hình an toàn");
        }
        String normalizedPlanCode = normalizePlanCode(planCode);
        String normalizedRequestId = normalizeClientRequestId(clientRequestId);
        if (userId <= 0 || acceptedPlanVersion <= 0) {
            throw new IllegalArgumentException("Yêu cầu khởi tạo mandate không hợp lệ");
        }
        String requestHash = ContentHashUtil.sha256Hex("VNPAY_RECURRING_MANDATE|" + userId + '|'
            + normalizedRequestId + '|' + normalizedPlanCode + '|' + acceptedPlanVersion);
        ReadingSubscriptionMandateRow replay = mandateMapper.selectByClientRequestId(
            userId, normalizedRequestId);
        if (replay != null) {
            return replayInitialization(replay, requestHash);
        }
        ReadingSubscriptionPlanRow plan = subscriptionMapper.selectActivePlanByCode(normalizedPlanCode);
        if (plan == null || plan.getPlanVersion() == null || plan.getPlanVersion() != acceptedPlanVersion
            || plan.getPriceVnd() == null || plan.getPriceVnd() <= 0
            || plan.getPeriodMonths() == null || plan.getPeriodMonths() <= 0) {
            throw new IllegalArgumentException("Gói thuê bao hoặc phiên bản giá không hợp lệ");
        }
        ZoneId zoneId = ZoneId.of(properties.getTimeZone());
        LocalDateTime now = LocalDateTime.now(zoneId);
        String merchantReference = merchantReference(userId, now);
        try {
            mandateMapper.insertPending(userId, merchantReference, normalizedRequestId, requestHash,
                normalizedPlanCode, acceptedPlanVersion);
        } catch (DataIntegrityViolationException exception) {
            replay = mandateMapper.selectByClientRequestId(userId, normalizedRequestId);
            if (replay != null) {
                return replayInitialization(replay, requestHash);
            }
            throw new IllegalStateException("Tài khoản đã có ủy quyền VNPAY đang mở", exception);
        }

        VnpayRecurringMandateCommand command = new VnpayRecurringMandateCommand(
            requestId(), merchantReference, userId, plan.getPriceVnd(), plan.getPeriodMonths(),
            now.toLocalDate().plusMonths(plan.getPeriodMonths()), now,
            normalizeIp(ipAddress), normalizeUserAgent(userAgent));
        try {
            VnpayRecurringMandateInitialization initialization = client.initializeMandate(command);
            String encryptedDataKey = piiCryptoService.encrypt(initialization.dataKey());
            if (mandateMapper.registerProviderInitialization(merchantReference,
                initialization.providerRecurringId(), encryptedDataKey) != 1) {
                throw new VnpayRecurringUnavailableException("Không thể ghi nhận giao dịch VNPAY Recurring");
            }
            return initialization;
        } catch (RuntimeException exception) {
            failPendingMandate(merchantReference, exception);
            throw exception;
        }
    }

    public VnpayRecurringMandateState getState(long userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("User mandate không hợp lệ");
        }
        ReadingSubscriptionMandateRow row = mandateMapper.selectOpenByUser(userId);
        return row == null
            ? new VnpayRecurringMandateState(properties.isConfigured(), "NONE", null, null, null)
            : new VnpayRecurringMandateState(properties.isConfigured(), row.getStatus(),
                row.getClientRequestId(), row.getPlanCodeSnapshot(), row.getAcceptedPlanVersion());
    }

    private VnpayRecurringMandateInitialization replayInitialization(
        ReadingSubscriptionMandateRow row, String expectedRequestHash) {
        if (!expectedRequestHash.equals(row.getRequestHash())) {
            throw new IllegalStateException("Mã yêu cầu mandate đã được dùng cho nội dung khác");
        }
        if ("FAILED".equals(row.getStatus())) {
            throw new VnpayRecurringUnavailableException(
                "Lần khởi tạo mandate trước đã thất bại; cần dùng mã yêu cầu mới");
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new IllegalStateException("Mandate không còn ở trạng thái khởi tạo");
        }
        if (row.getProviderRecurringId() == null || row.getProviderRecurringId().isBlank()
            || row.getProviderDataKeyCiphertext() == null
            || row.getProviderDataKeyCiphertext().isBlank()) {
            throw new IllegalStateException("Yêu cầu mandate đang được xử lý");
        }
        String dataKey = piiCryptoService.decrypt(row.getProviderDataKeyCiphertext());
        if (dataKey == null || dataKey.isBlank()) {
            throw new VnpayRecurringUnavailableException("Dữ liệu replay mandate không hợp lệ");
        }
        return new VnpayRecurringMandateInitialization(row.getMerchantReference(),
            row.getProviderRecurringId(), properties.getPayUrl(), properties.getTmnCode(), dataKey);
    }

    private void failPendingMandate(String merchantReference, RuntimeException originalFailure) {
        try {
            ReadingSubscriptionMandateRow row = mandateMapper.selectByMerchantReference(merchantReference);
            if (row != null && "PENDING".equals(row.getStatus())) {
                mandateMapper.fail(row.getId(), row.getVersion());
            }
        } catch (RuntimeException cleanupFailure) {
            if (cleanupFailure != originalFailure) {
                originalFailure.addSuppressed(cleanupFailure);
            }
        }
    }

    @Transactional
    public CallbackResult processIpn(Map<String, String> parameters) {
        if (!properties.isConfigured() || !signer.verifyCallback(parameters)
            || !properties.getTmnCode().equals(parameters.get("vnp_tmn_code"))) {
            return CallbackResult.INVALID_CHECKSUM;
        }
        if (!"recurring".equals(parameters.get("vnp_command"))
            || !"00".equals(parameters.get("vnp_response_code"))
            || !"00".equals(parameters.get("vnp_transaction_status"))) {
            return CallbackResult.INVALID_REQUEST;
        }
        String merchantReference = parameters.get("vnp_txn_ref");
        ReadingSubscriptionMandateRow row = mandateMapper.selectByMerchantReferenceForUpdate(merchantReference);
        if (row == null) {
            return CallbackResult.NOT_FOUND;
        }
        if ("ACTIVE".equals(row.getStatus())) {
            return CallbackResult.ALREADY_PROCESSED;
        }
        if (!"PENDING".equals(row.getStatus())
            || !String.valueOf(row.getUserId()).equals(parameters.get("vnp_app_user_id"))) {
            return CallbackResult.INVALID_REQUEST;
        }
        String token = parameters.get("vnp_token");
        if (token == null || token.isBlank() || token.length() > 256) {
            return CallbackResult.INVALID_REQUEST;
        }
        Date tokenExpiry;
        try {
            String expiry = parameters.get("vnp_token_exp_date");
            tokenExpiry = expiry == null || expiry.isBlank() ? null
                : Date.from(LocalDate.parse(expiry, TOKEN_EXPIRY).plusDays(1)
                    .atStartOfDay(ZoneId.of(properties.getTimeZone())).toInstant());
        } catch (RuntimeException exception) {
            return CallbackResult.INVALID_REQUEST;
        }
        String ciphertext = piiCryptoService.encrypt(token);
        int updated = mandateMapper.activate(row.getId(), row.getVersion(), ciphertext,
            tokenExpiry, new Date());
        if (updated == 1) {
            return CallbackResult.SUCCESS;
        }
        ReadingSubscriptionMandateRow current = mandateMapper.selectByMerchantReference(merchantReference);
        return current != null && "ACTIVE".equals(current.getStatus())
            ? CallbackResult.ALREADY_PROCESSED : CallbackResult.SYSTEM_ERROR;
    }

    public String inspectReturn(Map<String, String> parameters) {
        if (!properties.isConfigured() || !signer.verifyCallback(parameters)
            || !properties.getTmnCode().equals(parameters.get("vnp_tmn_code"))
            || !"recurring".equals(parameters.get("vnp_command"))) {
            return "failed";
        }
        ReadingSubscriptionMandateRow row = mandateMapper.selectByMerchantReference(
            parameters.get("vnp_txn_ref"));
        if (row == null || !String.valueOf(row.getUserId()).equals(parameters.get("vnp_app_user_id"))) {
            return "failed";
        }
        return "ACTIVE".equals(row.getStatus()) ? "success" : "processing";
    }

    private String merchantReference(long userId, LocalDateTime now) {
        return "NP" + userId + now.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
            + String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private String requestId() {
        return String.valueOf(System.currentTimeMillis()) + String.format("%04d", RANDOM.nextInt(10_000));
    }

    private String normalizePlanCode(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{2,32}")) {
            throw new IllegalArgumentException("Mã gói mandate không hợp lệ");
        }
        return normalized;
    }

    private String normalizeClientRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Mã yêu cầu mandate không hợp lệ");
        }
        return normalized;
    }

    private String normalizeIp(String value) {
        if (value == null || value.isBlank() || value.length() > 30
            || !value.matches("[0-9A-Fa-f:.]+")) {
            return "127.0.0.1";
        }
        return value;
    }

    private String normalizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return "NovelPlusWeb";
        }
        String normalized = value.replaceAll("[^A-Za-z0-9 ]", " ").replaceAll(" +", " ").trim();
        if (normalized.isBlank()) {
            return "NovelPlusWeb";
        }
        return normalized.substring(0, Math.min(normalized.length(), 255));
    }

    public enum CallbackResult {
        SUCCESS, ALREADY_PROCESSED, NOT_FOUND, INVALID_CHECKSUM, INVALID_REQUEST, SYSTEM_ERROR
    }
}
