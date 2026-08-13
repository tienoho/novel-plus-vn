package com.java2nb.common.exception;

import com.java2nb.common.utils.R;
import com.java2nb.novel.service.gamification.config.GamificationConfigConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GamificationConfigConflictHandlerTest {

    @Test
    void optimisticConflictReturnsHttp409WithoutChangingTheErrorType() {
        CommonExceptionHandler handler = new CommonExceptionHandler();

        ResponseEntity<R> response = handler.handleGamificationConfigConflict(
            new GamificationConfigConflictException("Revision đã được người khác cập nhật"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo(409);
        assertThat(response.getBody().get("msg")).isEqualTo(
            "Revision đã được người khác cập nhật");
    }
}
