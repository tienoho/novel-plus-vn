package com.java2nb.novel.dto.author;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StoryItemCreateRequest {
    @NotNull(message = "{author.story.validation.book}")
    private Long bookId;

    @NotBlank(message = "{author.story.validation.type}")
    @Size(max = 16, message = "{author.story.validation.type}")
    private String type;

    @NotBlank(message = "{author.story.validation.title}")
    @Size(max = 120, message = "{author.story.validation.title}")
    private String title;

    @Size(max = 20000, message = "{author.story.validation.content}")
    private String content;

    @Size(max = 100, message = "{author.story.validation.timeline}")
    private String timelineLabel;

    @Min(value = 0, message = "{author.story.validation.order}")
    @Max(value = 1000000, message = "{author.story.validation.order}")
    private Integer sortOrder;
}
