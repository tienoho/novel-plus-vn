package com.java2nb.novel.dto.author;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuthorAiRequest {
    @NotNull
    @Min(1)
    private Long bookId;

    @Min(1)
    private Long draftId;

    @NotBlank
    @Size(max = 20_000)
    private String text;

    @DecimalMin("1.0")
    @DecimalMax("500.0")
    private Double ratio;

    @Min(1)
    @Max(4_000)
    private Integer length;
}
