package com.java2nb.novel.service.reader;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.utils.AgeRatingUtil;
import com.java2nb.novel.dto.reader.ReaderAnnotationCreateRequest;
import com.java2nb.novel.dto.reader.ReaderAnnotationUpdateRequest;
import com.java2nb.novel.dto.reader.ReaderProgressUpdateRequest;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.ReaderStateMapper;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.chapter.ChapterCommercialPolicyService;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.List;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class ReaderStateServiceImpl implements ReaderStateService {
    private final ReaderStateMapper mapper;
    private final UserService userService;
    private final BookService bookService;
    private final ChapterCommercialPolicyService chapterCommercialPolicyService;
    private final ReadingTicketService readingTicketService;

    @Override
    @Transactional(readOnly = true)
    public ReaderStateView getState(long userId, long bookId, long bookIndexId) {
        requireReadableChapter(userId, bookId, bookIndexId);
        ReaderProgressRow progress = mapper.selectProgress(userId, bookId);
        List<ReaderAnnotationRow> annotations = mapper.listAnnotations(userId, bookId, bookIndexId);
        return new ReaderStateView(progress, annotations);
    }

    @Override
    @Transactional
    public ReaderProgressRow saveProgress(long userId, ReaderProgressUpdateRequest input) {
        requireReadableChapter(userId, input.getBookId(), input.getBookIndexId());
        mapper.upsertProgress(userId, input.getBookId(), input.getBookIndexId(), input.getParagraphIndex(),
            input.getCharacterOffset(), input.getProgressPercent().setScale(2, RoundingMode.HALF_UP));
        return mapper.selectProgress(userId, input.getBookId());
    }

    @Override
    @Transactional
    public ReaderAnnotationRow createAnnotation(long userId, ReaderAnnotationCreateRequest input) {
        requireReadableChapter(userId, input.getBookId(), input.getBookIndexId());
        ReaderAnnotationType type = ReaderAnnotationType.parse(input.getType());
        String noteText = normalizeNote(type, input.getNoteText());
        ReaderAnnotationRow annotation = new ReaderAnnotationRow();
        annotation.setUserId(userId);
        annotation.setBookId(input.getBookId());
        annotation.setBookIndexId(input.getBookIndexId());
        annotation.setType(type.name());
        annotation.setParagraphIndex(input.getParagraphIndex());
        annotation.setCharacterOffset(input.getCharacterOffset());
        annotation.setSelectedText(normalizeNullable(input.getSelectedText()));
        annotation.setNoteText(noteText);
        annotation.setVersion(0L);
        mapper.insertAnnotation(annotation);
        return requireAnnotation(userId, annotation.getId());
    }

    @Override
    @Transactional
    public ReaderAnnotationRow updateAnnotation(long userId, long annotationId,
                                                 ReaderAnnotationUpdateRequest input) {
        ReaderAnnotationRow current = requireAnnotation(userId, annotationId);
        requireReadableChapter(userId, current.getBookId(), current.getBookIndexId());
        String noteText = normalizeNote(ReaderAnnotationType.parse(current.getType()), input.getNoteText());
        if (mapper.updateAnnotationOwned(userId, annotationId, noteText, input.getExpectedVersion()) != 1) {
            if (mapper.selectAnnotationOwned(userId, annotationId) == null) {
                throw new BusinessException(ResponseStatus.READER_ANNOTATION_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_VERSION_CONFLICT);
        }
        return requireAnnotation(userId, annotationId);
    }

    @Override
    @Transactional
    public void deleteAnnotation(long userId, long annotationId, long expectedVersion) {
        requireAnnotation(userId, annotationId);
        if (mapper.deleteAnnotationOwned(userId, annotationId, expectedVersion) != 1) {
            if (mapper.selectAnnotationOwned(userId, annotationId) == null) {
                throw new BusinessException(ResponseStatus.READER_ANNOTATION_NOT_FOUND);
            }
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_VERSION_CONFLICT);
        }
    }

    @Override
    public void requireReadableChapter(long userId, long bookId, long bookIndexId) {
        Book book = bookService.queryBookDetail(bookId);
        ResponseStatus denial = AgeRatingUtil.publicBookDenialReason(book, userService.userInfo(userId));
        if (denial != null) {
            throw new BusinessException(denial);
        }

        BookIndex chapter = bookService.queryBookIndex(bookIndexId);
        denial = AgeRatingUtil.publicChapterDenialReason(chapter, bookId);
        if (denial != null) {
            throw new BusinessException(denial);
        }

        Date now = new Date();
        boolean purchased = userService.queryIsBuyBookIndex(userId, bookIndexId);
        boolean entitled = !purchased
            && readingTicketService.hasActiveChapterEntitlement(userId, bookIndexId, now);
        if (chapterCommercialPolicyService.evaluate(chapter, purchased || entitled, now).purchaseRequired()) {
            throw new BusinessException(ResponseStatus.BOOK_NOT_AVAILABLE);
        }
    }

    private ReaderAnnotationRow requireAnnotation(long userId, Long annotationId) {
        if (annotationId == null) {
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_NOT_FOUND);
        }
        ReaderAnnotationRow annotation = mapper.selectAnnotationOwned(userId, annotationId);
        if (annotation == null) {
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_NOT_FOUND);
        }
        return annotation;
    }

    private String normalizeNote(ReaderAnnotationType type, String value) {
        String normalized = normalizeNullable(value);
        if (type == ReaderAnnotationType.NOTE && normalized == null) {
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_INVALID);
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
