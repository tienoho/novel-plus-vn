package com.java2nb.novel.service.reader;

import com.java2nb.novel.dto.reader.ReaderAnnotationCreateRequest;
import com.java2nb.novel.dto.reader.ReaderAnnotationUpdateRequest;
import com.java2nb.novel.dto.reader.ReaderProgressUpdateRequest;

public interface ReaderStateService {
    ReaderStateView getState(long userId, long bookId, long bookIndexId);

    ReaderProgressRow saveProgress(long userId, ReaderProgressUpdateRequest input);

    ReaderAnnotationRow createAnnotation(long userId, ReaderAnnotationCreateRequest input);

    ReaderAnnotationRow updateAnnotation(long userId, long annotationId, ReaderAnnotationUpdateRequest input);

    void deleteAnnotation(long userId, long annotationId, long expectedVersion);
}
