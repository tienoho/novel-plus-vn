package com.java2nb.novel.controller;

import com.java2nb.novel.mapper.BookMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImageModerationControllerTest {

    private BookMapper bookMapper;
    private ImageModerationController controller;

    @BeforeEach
    void setUp() {
        bookMapper = mock(BookMapper.class);
        controller = new ImageModerationController(bookMapper);
        when(bookMapper.selectMany(any(SelectStatementProvider.class))).thenReturn(List.of());
        when(bookMapper.count(any(SelectStatementProvider.class))).thenReturn(0L);
    }

    @Test
    void pendingQueueFiltersByDedicatedCoverStatus() {
        controller.list(Map.of("offset", "0", "limit", "20", "auditStatus", "0"));

        ArgumentCaptor<SelectStatementProvider> statements =
            ArgumentCaptor.forClass(SelectStatementProvider.class);
        verify(bookMapper).selectMany(statements.capture());

        assertThat(statements.getValue().getSelectStatement())
            .contains("cover_audit_status")
            .contains("pic_url is not null");
        assertThat(statements.getValue().getParameters().values()).contains((byte) 0);
    }

    @Test
    void approveRemovesCoverFromPendingQueueWithoutChangingImage() {
        controller.audit(10L, true, null, "/images/default.gif");

        ArgumentCaptor<UpdateStatementProvider> update =
            ArgumentCaptor.forClass(UpdateStatementProvider.class);
        verify(bookMapper).update(update.capture());

        assertThat(update.getValue().getUpdateStatement())
            .contains("cover_audit_status")
            .contains("cover_audit_reason = null")
            .doesNotContain("pic_url =");
        assertThat(update.getValue().getParameters().values()).contains((byte) 1, 10L);
    }

    @Test
    void rejectStoresReasonAndReplacesUnsafeImage() {
        controller.audit(10L, false, "Bìa chứa nội dung bị cấm", "/images/default.gif");

        ArgumentCaptor<UpdateStatementProvider> update =
            ArgumentCaptor.forClass(UpdateStatementProvider.class);
        verify(bookMapper).update(update.capture());

        assertThat(update.getValue().getUpdateStatement())
            .contains("cover_audit_status")
            .contains("cover_audit_reason")
            .contains("pic_url");
        assertThat(update.getValue().getParameters().values())
            .contains((byte) 2, "Bìa chứa nội dung bị cấm", "/images/default.gif", 10L);
    }
}
