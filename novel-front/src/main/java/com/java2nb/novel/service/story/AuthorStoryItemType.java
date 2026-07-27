package com.java2nb.novel.service.story;

import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;

import java.util.Locale;

public enum AuthorStoryItemType {
    OUTLINE,
    CHARACTER,
    LOCATION,
    TIMELINE;

    public static AuthorStoryItemType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_INVALID_TYPE);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResponseStatus.AUTHOR_STORY_INVALID_TYPE);
        }
    }
}
