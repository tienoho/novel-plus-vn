package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationPublicPolicyMapper;
import com.java2nb.novel.service.impl.GamificationPublicPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationPublicPolicyServiceImplTest {

    private static final Date PUBLISHED_AT =
        Date.from(Instant.parse("2026-08-15T03:00:00Z"));
    private static final String CONTENT =
        "Nội dung luật chơi công khai mô tả thời hạn Đuốc, cách xếp hạng, "
            + "đối soát phần thưởng và cơ chế chống lạm dụng cho độc giả.";

    private GamificationPublicPolicyMapper mapper;
    private GamificationPublicPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationPublicPolicyMapper.class);
        service = new GamificationPublicPolicyServiceImpl(mapper);
    }

    @Test
    void createsDraftAndImmutableAuditWithNormalizedFields() {
        GamificationPublicPolicyRow created = policy(81L, "v2", "Luật chơi v2", CONTENT,
            "DRAFT", 0L);
        when(mapper.selectByVersion("v2")).thenReturn(null, created);
        when(mapper.insertDraft("v2", "Luật chơi v2", CONTENT)).thenReturn(1);
        when(mapper.insertAudit(81L, "CREATED", null, "DRAFT", 9L)).thenReturn(1);

        assertThat(service.createDraft(" v2 ", " Luật chơi v2 ", " " + CONTENT + " ", 9L))
            .isSameAs(created);

        verify(mapper).insertDraft("v2", "Luật chơi v2", CONTENT);
        verify(mapper).insertAudit(81L, "CREATED", null, "DRAFT", 9L);
    }

    @Test
    void retriesSameDraftWithoutCreatingDuplicateAudit() {
        GamificationPublicPolicyRow existing = policy(81L, "v2", "Luật chơi v2", CONTENT,
            "DRAFT", 0L);
        when(mapper.selectByVersion("v2")).thenReturn(existing);

        assertThat(service.createDraft("v2", "Luật chơi v2", CONTENT, 9L)).isSameAs(existing);

        verify(mapper, never()).insertDraft("v2", "Luật chơi v2", CONTENT);
        verify(mapper, never()).insertAudit(81L, "CREATED", null, "DRAFT", 9L);
    }

    @Test
    void publishesDraftAndArchivesPreviousPublishedPolicyInOneTransaction() {
        GamificationPublicPolicyRow target = policy(81L, "v2", "Luật chơi v2", CONTENT,
            "DRAFT", 3L);
        GamificationPublicPolicyRow current = policy(71L, "v1", "Luật chơi v1", CONTENT,
            "PUBLISHED", 1L);
        GamificationPublicPolicyRow published = policy(81L, "v2", "Luật chơi v2", CONTENT,
            "PUBLISHED", 4L);
        when(mapper.lockById(81L)).thenReturn(target);
        when(mapper.selectPublished()).thenReturn(current);
        when(mapper.archivePublished(PUBLISHED_AT)).thenReturn(1);
        when(mapper.insertAudit(71L, "ARCHIVED", "PUBLISHED", "ARCHIVED", 9L)).thenReturn(1);
        when(mapper.publish(81L, 3L, 9L, PUBLISHED_AT)).thenReturn(1);
        when(mapper.insertAudit(81L, "PUBLISHED", "DRAFT", "PUBLISHED", 9L)).thenReturn(1);
        when(mapper.selectById(81L)).thenReturn(published);

        assertThat(service.publish(81L, 3L, 9L, PUBLISHED_AT)).isSameAs(published);

        verify(mapper).archivePublished(PUBLISHED_AT);
        verify(mapper).publish(81L, 3L, 9L, PUBLISHED_AT);
    }

    @Test
    void rejectsStaleOptimisticVersionBeforeChangingPublishedPolicy() {
        GamificationPublicPolicyRow target = policy(81L, "v2", "Luật chơi v2", CONTENT,
            "DRAFT", 4L);
        when(mapper.lockById(81L)).thenReturn(target);

        assertThatThrownBy(() -> service.publish(81L, 3L, 9L, PUBLISHED_AT))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("version");

        verify(mapper, never()).archivePublished(PUBLISHED_AT);
        verify(mapper, never()).publish(81L, 3L, 9L, PUBLISHED_AT);
    }

    private GamificationPublicPolicyRow policy(long id, String policyVersion, String title,
                                                String content, String status, long version) {
        GamificationPublicPolicyRow row = new GamificationPublicPolicyRow();
        row.setId(id);
        row.setPolicyVersion(policyVersion);
        row.setTitle(title);
        row.setContentText(content);
        row.setStatus(status);
        row.setVersion(version);
        return row;
    }
}
