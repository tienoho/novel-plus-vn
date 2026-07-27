package com.java2nb.novel.core.schedule;

import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.service.AuthorChapterDraftService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthorChapterPublishSchedule {
    private final AuthorChapterDraftService draftService;

    @Value("${author.editor.schedule-batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${author.editor.schedule-delay-ms:30000}",
        initialDelayString = "${author.editor.schedule-initial-delay-ms:30000}")
    public void publishDueDrafts() {
        Date now = new Date();
        for (AuthorChapterDraft draft : draftService.listDue(now, batchSize)) {
            try {
                draftService.publishScheduled(draft.getId(), now);
            } catch (Exception exception) {
                log.error("Không thể xuất bản bản nháp theo lịch {}", draft.getDraftNo(), exception);
                draftService.recordScheduleFailure(draft.getId(), exception.getMessage());
            }
        }
    }
}
