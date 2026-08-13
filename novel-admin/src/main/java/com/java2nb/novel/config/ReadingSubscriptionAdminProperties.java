package com.java2nb.novel.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.reader-subscription")
public class ReadingSubscriptionAdminProperties {
    private boolean activationEnabled;
    private String policyVersion = "v1";

    public boolean isConfigured() {
        return policyVersion != null && !policyVersion.isBlank() && policyVersion.length() <= 32;
    }
}
