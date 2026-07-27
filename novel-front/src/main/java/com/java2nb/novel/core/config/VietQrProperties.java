package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.payment.vietqr")
public class VietQrProperties {
    private boolean enabled;
    private String bankBin;
    private String accountNo;
    private String accountName;
    private String secretToken;

    public boolean isConfigured() {
        return enabled
            && bankBin != null && bankBin.matches("[0-9]{6}")
            && accountNo != null && accountNo.matches("[0-9]{6,19}")
            && accountName != null && !accountName.isBlank() && accountName.length() <= 50
            && secretToken != null && secretToken.length() >= 32
            && !"disabled".equalsIgnoreCase(secretToken);
    }
}
