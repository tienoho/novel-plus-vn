package com.java2nb.novel.service.impl;

import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.AuthorChapterDraftMapper;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.AuthorChapterDraftService;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class AuthorChapterDraftServiceImpl implements AuthorChapterDraftService {
    private static final Pattern CLIENT_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final int MAX_TITLE_LENGTH = 100;
    private static final int MAX_CONTENT_LENGTH = 2_000_000;
    private static final long MIN_SCHEDULE_DELAY_MS = 30_000L;
    private static final long MAX_SCHEDULE_DELAY_MS = 366L * 24 * 60 * 60 * 1000;

    private final AuthorChapterDraftMapper draftMapper;
    private final BookIndexMapper bookIndexMapper;
    private final BookService bookService;
    private final AuthorBookCollaborationService collaborationService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorChapterDraft autosave(long authorId, DraftAutosaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu bản nháp");
        }
        String clientKey = normalizeClientKey(request.getClientKey());
        String indexName = normalizeTitle(request.getIndexName());
        String content = normalizeContent(request.getContent());
        byte isVip = normalizeVip(request.getIsVip());
        validateOwnership(authorId, request.getBookId(), request.getIndexId());

        if (request.getDraftId() == null) {
            AuthorChapterDraft existing = draftMapper.selectByClientKey(authorId, clientKey);
            if (existing != null) {
                if (samePayload(existing, request.getBookId(), request.getIndexId(), indexName, content, isVip)) {
                    return existing;
                }
                throw new IllegalStateException("Bản nháp đã tồn tại; hãy tải lại phiên bản mới nhất trước khi lưu");
            }
            Date now = new Date();
            AuthorChapterDraft draft = new AuthorChapterDraft();
            draft.setDraftNo("DR-" + UUID.randomUUID().toString().replace("-", ""));
            draft.setClientKey(clientKey);
            draft.setAuthorId(authorId);
            draft.setBookId(request.getBookId());
            draft.setIndexId(request.getIndexId());
            draft.setIndexName(indexName);
            draft.setContent(content);
            draft.setIsVip(isVip);
            draft.setStatus("DRAFT");
            draft.setVersion(0L);
            draft.setLastAutosaveAt(now);
            try {
                if (draftMapper.insert(draft) != 1) {
                    throw new IllegalStateException("Không thể tạo bản nháp");
                }
            } catch (DuplicateKeyException exception) {
                AuthorChapterDraft concurrent = draftMapper.selectByClientKey(authorId, clientKey);
                if (concurrent != null && samePayload(concurrent, request.getBookId(), request.getIndexId(),
                    indexName, content, isVip)) {
                    return concurrent;
                }
                throw exception;
            }
            audit(draft, "DRAFT_CREATED", null, "DRAFT", "AUTHOR", authorId, null);
            return requireOwned(authorId, draft.getId());
        }

        AuthorChapterDraft current = requireOwned(authorId, request.getDraftId());
        if (!Objects.equals(current.getClientKey(), clientKey)) {
            throw new IllegalArgumentException("Khóa trình soạn thảo không khớp bản nháp");
        }
        if (!Objects.equals(current.getBookId(), request.getBookId())
            || !Objects.equals(current.getIndexId(), request.getIndexId())) {
            throw new IllegalArgumentException("Không được đổi tác phẩm hoặc chương nguồn của bản nháp");
        }
        long expectedVersion = requireVersion(request.getExpectedVersion());
        if (draftMapper.updateAutosave(current.getId(), authorId, expectedVersion, indexName, content, isVip,
            new Date()) != 1) {
            throw concurrentChange();
        }
        return requireOwned(authorId, current.getId());
    }

    @Override
    public AuthorChapterDraft get(long authorId, long draftId) {
        AuthorChapterDraft draft = requireOwned(authorId, draftId);
        collaborationService.requirePermission(authorId, draft.getBookId(), BookPermission.MANAGE_CHAPTERS);
        return draft;
    }

    @Override
    public List<AuthorChapterDraft> list(long authorId, String status, int page, int pageSize) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(1, Math.min(pageSize, 100));
        return draftMapper.listByAuthor(authorId, normalizeStatus(status), (long) (safePage - 1) * safeSize,
            safeSize);
    }

    @Override
    public long count(long authorId, String status) {
        return draftMapper.countByAuthor(authorId, normalizeStatus(status));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorChapterDraft schedule(long authorId, long draftId, long expectedVersion, Date scheduledAt) {
        AuthorChapterDraft draft = requireOwned(authorId, draftId);
        collaborationService.requirePermission(authorId, draft.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        validatePublishable(draft);
        Date now = new Date();
        if (scheduledAt == null || scheduledAt.getTime() < now.getTime() + MIN_SCHEDULE_DELAY_MS
            || scheduledAt.getTime() > now.getTime() + MAX_SCHEDULE_DELAY_MS) {
            throw new IllegalArgumentException("Thời gian xuất bản phải từ 30 giây đến 366 ngày trong tương lai");
        }
        if (draftMapper.schedule(draftId, authorId, expectedVersion, scheduledAt) != 1) {
            throw concurrentChange();
        }
        audit(draft, "DRAFT_SCHEDULED", "DRAFT", "SCHEDULED", "AUTHOR", authorId,
            "Lịch xuất bản: " + scheduledAt.getTime());
        return requireOwned(authorId, draftId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorChapterDraft cancelSchedule(long authorId, long draftId, long expectedVersion) {
        AuthorChapterDraft draft = requireOwned(authorId, draftId);
        collaborationService.requirePermission(authorId, draft.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        if (draftMapper.cancelSchedule(draftId, authorId, expectedVersion) != 1) {
            throw concurrentChange();
        }
        audit(draft, "SCHEDULE_CANCELLED", "SCHEDULED", "DRAFT", "AUTHOR", authorId, null);
        return requireOwned(authorId, draftId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorChapterDraft publishNow(long authorId, long draftId, long expectedVersion) {
        AuthorChapterDraft draft = requireOwned(authorId, draftId);
        collaborationService.requirePermission(authorId, draft.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        if (!"DRAFT".equals(draft.getStatus()) && !"SCHEDULED".equals(draft.getStatus())) {
            throw new IllegalStateException("Bản nháp không ở trạng thái có thể xuất bản");
        }
        validatePublishable(draft);
        return publishClaimed(draft, expectedVersion, draft.getStatus(), "AUTHOR", authorId);
    }

    @Override
    public List<AuthorChapterDraft> listDue(Date now, int limit) {
        return draftMapper.listDueScheduled(now, Math.max(1, Math.min(limit, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public AuthorChapterDraft publishScheduled(long draftId, Date now) {
        AuthorChapterDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !"SCHEDULED".equals(draft.getStatus()) || draft.getScheduledAt() == null
            || draft.getScheduledAt().after(now)) {
            return draft;
        }
        validateOwnership(draft.getAuthorId(), draft.getBookId(), draft.getIndexId());
        collaborationService.requirePermission(
            draft.getAuthorId(), draft.getBookId(), BookPermission.PUBLISH_CHAPTERS);
        validatePublishable(draft);
        return publishClaimed(draft, draft.getVersion(), "SCHEDULED", "SYSTEM", null);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void recordScheduleFailure(long draftId, String message) {
        draftMapper.recordLastError(draftId, StringUtils.abbreviate(StringUtils.defaultString(message,
            "Lỗi xuất bản theo lịch"), 500));
    }

    private AuthorChapterDraft publishClaimed(AuthorChapterDraft draft, long expectedVersion, String fromStatus,
                                               String actorType, Long actorId) {
        if (draftMapper.claimForPublishing(draft.getId(), draft.getAuthorId(), expectedVersion, fromStatus) != 1) {
            throw concurrentChange();
        }
        String storedContent = toPublishedContent(draft.getContent());
        long publishedIndexId;
        if (draft.getIndexId() == null) {
            publishedIndexId = bookService.addBookContent(draft.getBookId(), draft.getIndexName(), storedContent,
                draft.getIsVip(), draft.getAuthorId());
        } else {
            bookService.updateBookContent(draft.getIndexId(), draft.getIndexName(), storedContent,
                draft.getAuthorId());
            publishedIndexId = draft.getIndexId();
        }
        if (draftMapper.markPublished(draft.getId(), draft.getAuthorId(), expectedVersion + 1,
            publishedIndexId) != 1) {
            throw concurrentChange();
        }
        audit(draft, "DRAFT_PUBLISHED", fromStatus, "PUBLISHED", actorType, actorId,
            "Chương đã xuất bản: " + publishedIndexId);
        return requireOwned(draft.getAuthorId(), draft.getId());
    }

    private void validateOwnership(long authorId, Long bookId, Long indexId) {
        if (bookId == null || bookId <= 0) {
            throw new IllegalArgumentException("Thiếu tác phẩm của bản nháp");
        }
        collaborationService.requirePermission(authorId, bookId, BookPermission.MANAGE_CHAPTERS);
        if (indexId != null) {
            BookIndex index = bookIndexMapper.selectByPrimaryKey(indexId).orElse(null);
            if (index == null || !bookId.equals(index.getBookId())) {
                throw new IllegalArgumentException("Chương nguồn không thuộc tác phẩm của bản nháp");
            }
        }
    }

    private AuthorChapterDraft requireOwned(long authorId, long draftId) {
        AuthorChapterDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !Long.valueOf(authorId).equals(draft.getAuthorId())) {
            throw new IllegalArgumentException("Không tìm thấy bản nháp của tác giả");
        }
        return draft;
    }

    private void validatePublishable(AuthorChapterDraft draft) {
        if (StringUtils.isBlank(draft.getIndexName())) {
            throw new IllegalArgumentException("Tên chương không được để trống khi xuất bản");
        }
        if (StringUtils.isBlank(draft.getContent())) {
            throw new IllegalArgumentException("Nội dung chương không được để trống khi xuất bản");
        }
    }

    private String normalizeClientKey(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        if (!CLIENT_KEY.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Khóa trình soạn thảo phải dài 8-64 ký tự chữ, số, gạch ngang hoặc gạch dưới");
        }
        return normalized;
    }

    private String normalizeTitle(String value) {
        String normalized = StringUtils.trimToEmpty(value);
        if (normalized.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("Tên chương không được vượt quá 100 ký tự");
        }
        return normalized;
    }

    private String normalizeContent(String value) {
        String normalized = StringUtils.defaultString(value).replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("Bản nháp không được vượt quá 2.000.000 ký tự");
        }
        return normalized;
    }

    private byte normalizeVip(Byte value) {
        byte normalized = value == null ? 0 : value;
        if (normalized != 0 && normalized != 1) {
            throw new IllegalArgumentException("Trạng thái chương thu phí không hợp lệ");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = StringUtils.trimToNull(status);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toUpperCase();
        if (!List.of("DRAFT", "SCHEDULED", "PUBLISHING", "PUBLISHED", "CANCELLED").contains(normalized)) {
            throw new IllegalArgumentException("Trạng thái bản nháp không hợp lệ");
        }
        return normalized;
    }

    private long requireVersion(Long version) {
        if (version == null || version < 0) {
            throw new IllegalArgumentException("Thiếu phiên bản bản nháp");
        }
        return version;
    }

    private boolean samePayload(AuthorChapterDraft draft, Long bookId, Long indexId, String indexName,
                                String content, byte isVip) {
        return Objects.equals(draft.getBookId(), bookId)
            && Objects.equals(draft.getIndexId(), indexId)
            && Objects.equals(draft.getIndexName(), indexName)
            && Objects.equals(draft.getContent(), content)
            && Objects.equals(draft.getIsVip(), isVip);
    }

    private String toPublishedContent(String content) {
        return HtmlUtils.htmlEscape(content, "UTF-8")
            .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
            .replace(" ", "&nbsp;")
            .replace("\n", "<br>");
    }

    private IllegalStateException concurrentChange() {
        return new IllegalStateException("Bản nháp đã thay đổi ở phiên khác; hãy tải lại trước khi tiếp tục");
    }

    private void audit(AuthorChapterDraft draft, String eventType, String fromStatus, String toStatus,
                       String actorType, Long actorId, String detail) {
        if (draftMapper.insertEvent(draft.getId(), draft.getDraftNo(), eventType, fromStatus, toStatus,
            actorType, actorId, detail) != 1) {
            throw new IllegalStateException("Không thể ghi audit bản nháp");
        }
    }
}
