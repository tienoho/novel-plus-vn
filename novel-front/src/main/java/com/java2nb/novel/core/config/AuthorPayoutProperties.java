package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "author.payout")
public class AuthorPayoutProperties {

    private boolean enabled;
    private long vndPerXu = 10;
    private long minimumXu = 100_000;
    private int requestOpenDay = 1;
    private int requestCloseDay = 10;

    public boolean isConfigured() {
        return enabled && vndPerXu > 0 && minimumXu > 0
            && requestOpenDay >= 1 && requestOpenDay <= 28
            && requestCloseDay >= requestOpenDay && requestCloseDay <= 28;
    }
}
