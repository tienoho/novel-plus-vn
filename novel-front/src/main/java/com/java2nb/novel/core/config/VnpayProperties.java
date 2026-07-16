package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "vnpay")
public class VnpayProperties {

    private boolean enabled;
    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String returnUrl;
    private String queryUrl = "https://sandbox.vnpayment.vn/merchant_webapi/api/transaction";
    private String version = "2.1.0";
    private String locale = "vn";
    private String orderType = "other";
    private String timeZone = "Asia/Ho_Chi_Minh";
    private int xuPerThousandVnd = 100;
    private List<Integer> allowedAmountsVnd = List.of(10_000, 30_000, 50_000, 100_000, 200_000, 500_000);
    private boolean reconciliationEnabled = true;
    private String serverIp = "127.0.0.1";
    private long reconciliationDelayMs = 300_000;
    private long reconciliationInitialDelayMs = 60_000;
    private int reconciliationMinAgeMinutes = 20;
    private int reconciliationMaxAgeDays = 30;
    private int reconciliationBatchSize = 50;

    public boolean isConfigured() {
        return enabled && tmnCode != null && tmnCode.matches("[A-Za-z0-9]{8}") && hasText(hashSecret)
            && isHttpUrl(payUrl) && isHttpUrl(returnUrl) && hasValidTimeZone() && xuPerThousandVnd > 0
            && allowedAmountsVnd != null && !allowedAmountsVnd.isEmpty()
            && allowedAmountsVnd.stream().allMatch(amount -> amount != null && amount > 0 && amount % 1_000 == 0)
            && (!reconciliationEnabled || isValidReconciliationConfig());
    }

    public boolean isAllowedAmount(int amountVnd) {
        return allowedAmountsVnd != null && allowedAmountsVnd.contains(amountVnd);
    }

    public List<Integer> getDisplayAmountsVnd() {
        if (allowedAmountsVnd == null) {
            return List.of();
        }
        return allowedAmountsVnd.stream()
            .filter(amount -> amount != null && amount > 0 && amount % 1_000 == 0)
            .distinct()
            .sorted()
            .toList();
    }

    public int calculateXu(int amountVnd) {
        if (amountVnd <= 0 || amountVnd % 1_000 != 0 || xuPerThousandVnd <= 0) {
            throw new IllegalArgumentException("Số tiền hoặc tỷ lệ quy đổi VNPAY không hợp lệ");
        }
        return Math.multiplyExact(amountVnd / 1_000, xuPerThousandVnd);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"disabled".equalsIgnoreCase(value);
    }

    private boolean isHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasValidTimeZone() {
        try {
            ZoneId.of(timeZone);
            return true;
        } catch (DateTimeException | NullPointerException exception) {
            return false;
        }
    }

    private boolean isValidReconciliationConfig() {
        return isHttpUrl(queryUrl) && hasText(serverIp) && serverIp.length() <= 45
            && serverIp.matches("[0-9A-Fa-f:.]+")
            && reconciliationDelayMs >= 60_000 && reconciliationInitialDelayMs >= 0
            && reconciliationMinAgeMinutes >= 15 && reconciliationMaxAgeDays > 0
            && reconciliationBatchSize > 0 && reconciliationBatchSize <= 500;
    }
}
