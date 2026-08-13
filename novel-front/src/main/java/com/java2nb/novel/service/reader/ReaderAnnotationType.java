package com.java2nb.novel.service.reader;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;

import java.util.Locale;

public enum ReaderAnnotationType {
    BOOKMARK,
    NOTE;

    public static ReaderAnnotationType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_INVALID);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException(ResponseStatus.READER_ANNOTATION_INVALID);
        }
    }
}
