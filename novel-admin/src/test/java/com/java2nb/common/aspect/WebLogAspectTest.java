package com.java2nb.common.aspect;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WebLogAspectTest {
    @Test
    void returningControllerResponseNeverLogsSensitivePayload() throws Throwable {
        Logger logger = (Logger) LoggerFactory.getLogger(WebLogAspect.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            new WebLogAspect().doAfterReturning(Map.of(
                "data", List.of("ABCD-EFGH-JKLM-NPQR-2345")));

            assertThat(appender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .contains("Yêu cầu đã được xử lý thành công")
                .noneMatch(message -> message.contains("ABCD-EFGH-JKLM-NPQR-2345"));
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }
}
