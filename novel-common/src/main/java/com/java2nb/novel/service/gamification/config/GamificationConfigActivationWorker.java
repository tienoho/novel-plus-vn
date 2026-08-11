package com.java2nb.novel.service.gamification.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class GamificationConfigActivationWorker {

    private static final long SYSTEM_ACTOR_ID = 1L;
    private final GamificationRuntimeConfigLifecycleService lifecycle;
    private final Counter activationFailures;

    public GamificationConfigActivationWorker(GamificationRuntimeConfigLifecycleService lifecycle,
                                              MeterRegistry registry) {
        this.lifecycle = lifecycle;
        this.activationFailures = Counter.builder("gamification.config.activation.failures")
            .description("Số lần worker kích hoạt revision gamification thất bại")
            .register(registry);
    }

    @Scheduled(fixedDelayString = "${GAMIFICATION_CONFIG_REFRESH_MS:2000}")
    public void activateDueRevision() {
        try {
            lifecycle.activateDue(SYSTEM_ACTOR_ID);
        } catch (RuntimeException exception) {
            activationFailures.increment();
            log.error("Không thể kích hoạt revision cấu hình gamification đến hạn", exception);
        }
    }
}
