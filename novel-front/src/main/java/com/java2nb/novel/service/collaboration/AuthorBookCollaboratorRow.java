package com.java2nb.novel.service.collaboration;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorBookCollaboratorRow {
    private Long id;
    private Long bookId;
    private Long ownerAuthorId;
    private Long collaboratorAuthorId;
    private String username;
    private String penName;
    private String role;
    private Boolean canEditBook;
    private Boolean canPublishBook;
    private Boolean canManageChapters;
    private Boolean canPublishChapters;
    private Boolean canManageStory;
    private Boolean canViewAnalytics;
    private Long version;
    private Long createdByAuthorId;
    private Long updatedByAuthorId;
    private Date createTime;
    private Date updateTime;
}
