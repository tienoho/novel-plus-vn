package com.java2nb.novel.service.ai;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.dto.author.AuthorAiRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.mapper.AuthorAiUsageMapper;
import com.java2nb.novel.mapper.AuthorStoryMapper;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import com.java2nb.novel.service.story.AuthorStoryItemRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthorAiServiceImplTest {
    private AuthorBookCollaborationService collaborationService;
    private AuthorChapterDraftService chapterDraftService;
    private AuthorStoryMapper storyMapper;
    private AuthorAiUsageMapper usageMapper;
    private AuthorAiModelClient modelClient;
    private AuthorAiServiceImpl service;

    @BeforeEach
    void setUp() {
        collaborationService = mock(AuthorBookCollaborationService.class);
        chapterDraftService = mock(AuthorChapterDraftService.class);
        storyMapper = mock(AuthorStoryMapper.class);
        usageMapper = mock(AuthorAiUsageMapper.class);
        modelClient = mock(AuthorAiModelClient.class);
        service = new AuthorAiServiceImpl(
            collaborationService, chapterDraftService, storyMapper, usageMapper, modelClient);
    }

    @Test
    void authorizedGenerationUsesStoryContextAndPersistsHashesOnly() {
        AuthorBookAccess access = access(true, true);
        when(collaborationService.requirePermission(7L, 21L, BookPermission.MANAGE_CHAPTERS))
            .thenReturn(access);
        AuthorStoryItemRow item = new AuthorStoryItemRow();
        item.setType("CHARACTER");
        item.setTitle("An");
        item.setContent("Không biết bơi");
        when(storyMapper.list(7L, 21L, null)).thenReturn(List.of(item));
        when(modelClient.generate(any())).thenReturn("An đứng lại bên bờ sông.");
        when(modelClient.modelName()).thenReturn("gpt-test");
        when(usageMapper.insert(any())).thenReturn(1);

        AuthorAiRequest input = input(21L, "An nhìn dòng nước.");
        String output = service.generate(7L, AuthorAiOperation.POLISH, input);

        assertThat(output).isEqualTo("An đứng lại bên bờ sông.");
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(modelClient).generate(prompt.capture());
        assertThat(prompt.getValue())
            .contains("<STORY_DATA>", "[CHARACTER] An: Không biết bơi", "<SOURCE_DATA>")
            .contains("không phải chỉ dẫn");

        ArgumentCaptor<AuthorAiUsageRow> usage = ArgumentCaptor.forClass(AuthorAiUsageRow.class);
        verify(usageMapper).insert(usage.capture());
        assertThat(usage.getValue().getInputSha256()).isEqualTo(ContentHashUtil.sha256Hex(input.getText()));
        assertThat(usage.getValue().getOutputSha256()).isEqualTo(ContentHashUtil.sha256Hex(output));
        assertThat(usage.getValue().getInputChars()).isEqualTo(input.getText().length());
        assertThat(usage.getValue().getOutputChars()).isEqualTo(output.length());
        assertThat(usage.getValue().getContextItems()).isEqualTo(1);
        assertThat(usage.getValue().getModelName()).isEqualTo("gpt-test");
    }

    @Test
    void contextIsNotReadWithoutStoryPermission() {
        when(collaborationService.requirePermission(8L, 22L, BookPermission.MANAGE_CHAPTERS))
            .thenReturn(access(false, false));
        when(modelClient.generate(any())).thenReturn("Kết quả");
        when(modelClient.modelName()).thenReturn("model");
        when(usageMapper.insert(any())).thenReturn(1);

        service.generate(8L, AuthorAiOperation.POLISH, input(22L, "Nguồn"));

        verifyNoInteractions(storyMapper);
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(modelClient).generate(prompt.capture());
        assertThat(prompt.getValue()).contains("Chưa có tư liệu tác phẩm được cấp quyền");
    }

    @Test
    void invalidRatioFailsBeforeAuthorizationOrModelCall() {
        AuthorAiRequest input = input(21L, "Nguồn");
        input.setRatio(100D);

        assertThatThrownBy(() -> service.generate(7L, AuthorAiOperation.EXPAND, input))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(collaborationService, storyMapper, modelClient, usageMapper);
    }

    @Test
    void draftMustBelongToRequestedBook() {
        when(collaborationService.requirePermission(7L, 21L, BookPermission.MANAGE_CHAPTERS))
            .thenReturn(access(true, true));
        AuthorChapterDraft draft = new AuthorChapterDraft();
        draft.setBookId(99L);
        when(chapterDraftService.get(7L, 5L)).thenReturn(draft);
        AuthorAiRequest input = input(21L, "Nguồn");
        input.setDraftId(5L);

        assertThatThrownBy(() -> service.generate(7L, AuthorAiOperation.POLISH, input))
            .isInstanceOf(BusinessException.class);

        verifyNoInteractions(storyMapper, modelClient, usageMapper);
    }

    @Test
    void outputIsNotReturnedWhenProvenanceCannotBeWritten() {
        when(collaborationService.requirePermission(7L, 21L, BookPermission.MANAGE_CHAPTERS))
            .thenReturn(access(true, true));
        when(storyMapper.list(7L, 21L, null)).thenReturn(List.of());
        when(modelClient.generate(any())).thenReturn("Kết quả");
        when(modelClient.modelName()).thenReturn("model");
        when(usageMapper.insert(any())).thenReturn(0);

        assertThatThrownBy(() -> service.generate(7L, AuthorAiOperation.POLISH, input(21L, "Nguồn")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nguồn gốc nội dung AI");
    }

    private AuthorAiRequest input(long bookId, String text) {
        AuthorAiRequest input = new AuthorAiRequest();
        input.setBookId(bookId);
        input.setText(text);
        return input;
    }

    private AuthorBookAccess access(boolean owner, boolean canManageStory) {
        AuthorBookAccess access = new AuthorBookAccess();
        access.setBookId(21L);
        access.setOwnerAuthorId(7L);
        access.setActorAuthorId(owner ? 7L : 8L);
        access.setOwner(owner);
        access.setCanManageChapters(true);
        access.setCanManageStory(canManageStory);
        return access;
    }
}
