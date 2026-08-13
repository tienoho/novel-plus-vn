package com.java2nb.novel.controller;

import com.java2nb.novel.dto.gamification.GamificationPublicPolicyResponse;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.GamificationPublicPolicyService;
import io.github.xxyopen.model.resp.RestResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GamificationPolicyControllerTest {

    private GamificationPublicPolicyService service;
    private GamificationPolicyController controller;

    @BeforeEach
    void setUp() {
        service = mock(GamificationPublicPolicyService.class);
        controller = new GamificationPolicyController(service);
    }

    @Test
    void returnsOnlyPublishedPublicFieldsAndKeepsContentAsPlainText() {
        Date publishedAt = Date.from(Instant.parse("2026-08-15T03:00:00Z"));
        GamificationPublicPolicyRow row = new GamificationPublicPolicyRow();
        row.setId(81L);
        row.setPolicyVersion("v2");
        row.setTitle("Luật chơi v2");
        row.setContentText("<script>alert('không được thực thi')</script>");
        row.setStatus("PUBLISHED");
        row.setPublishedBy(9L);
        row.setPublishedAt(publishedAt);
        row.setVersion(4L);
        when(service.getPublished()).thenReturn(row);

        RestResult<GamificationPublicPolicyResponse> result = controller.getPublishedPolicy();

        assertThat(result.getData()).isEqualTo(new GamificationPublicPolicyResponse(
            "v2", "Luật chơi v2", "<script>alert('không được thực thi')</script>", publishedAt));
    }

    @Test
    void returnsNullDataWhenNoPolicyHasBeenPublished() {
        assertThat(controller.getPublishedPolicy().getData()).isNull();
    }
}
