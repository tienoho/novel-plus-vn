package com.java2nb.novel.service.collaboration;

import lombok.Data;

@Data
public class AuthorBookAccess {
    private Long bookId;
    private Long ownerAuthorId;
    private Long actorAuthorId;
    private String role;
    private Boolean owner;
    private Boolean canEditBook;
    private Boolean canPublishBook;
    private Boolean canManageChapters;
    private Boolean canPublishChapters;
    private Boolean canManageStory;
    private Boolean canViewAnalytics;

    public boolean allows(BookPermission permission) {
        if (Boolean.TRUE.equals(owner)) {
            return true;
        }
        return switch (permission) {
            case EDIT_BOOK -> Boolean.TRUE.equals(canEditBook);
            case PUBLISH_BOOK -> Boolean.TRUE.equals(canPublishBook);
            case MANAGE_CHAPTERS -> Boolean.TRUE.equals(canManageChapters);
            case PUBLISH_CHAPTERS -> Boolean.TRUE.equals(canPublishChapters);
            case MANAGE_STORY -> Boolean.TRUE.equals(canManageStory);
            case VIEW_ANALYTICS -> Boolean.TRUE.equals(canViewAnalytics);
        };
    }
}
