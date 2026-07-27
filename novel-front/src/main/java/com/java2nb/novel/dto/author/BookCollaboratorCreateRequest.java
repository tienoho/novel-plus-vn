package com.java2nb.novel.dto.author;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookCollaboratorCreateRequest {
    @NotBlank(message = "{author.collaboration.validation.username}")
    @Size(max = 32, message = "{author.collaboration.validation.username}")
    private String username;

    @NotBlank(message = "{author.collaboration.validation.role}")
    @Size(max = 16, message = "{author.collaboration.validation.role}")
    private String role;

    private Boolean canEditBook;
    private Boolean canPublishBook;
    private Boolean canManageChapters;
    private Boolean canPublishChapters;
    private Boolean canManageStory;
    private Boolean canViewAnalytics;
}
