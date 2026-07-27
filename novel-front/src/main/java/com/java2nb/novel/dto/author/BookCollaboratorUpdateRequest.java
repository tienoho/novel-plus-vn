package com.java2nb.novel.dto.author;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BookCollaboratorUpdateRequest {
    @NotBlank(message = "{author.collaboration.validation.role}")
    @Size(max = 16, message = "{author.collaboration.validation.role}")
    private String role;

    private Boolean canEditBook;
    private Boolean canPublishBook;
    private Boolean canManageChapters;
    private Boolean canPublishChapters;
    private Boolean canManageStory;
    private Boolean canViewAnalytics;

    @NotNull(message = "{author.collaboration.validation.version}")
    @Min(value = 0, message = "{author.collaboration.validation.version}")
    private Long expectedVersion;
}
