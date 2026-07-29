package com.java2nb.novel.dto.reader;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReaderAnnotationCreateRequest {
    @NotNull(message = "{reader.state.validation.book}")
    private Long bookId;

    @NotNull(message = "{reader.state.validation.chapter}")
    private Long bookIndexId;

    @NotBlank(message = "{reader.state.validation.type}")
    @Size(max = 16, message = "{reader.state.validation.type}")
    private String type;

    @NotNull(message = "{reader.state.validation.position}")
    @Min(value = 0, message = "{reader.state.validation.position}")
    @Max(value = 1000000, message = "{reader.state.validation.position}")
    private Integer paragraphIndex;

    @NotNull(message = "{reader.state.validation.position}")
    @Min(value = 0, message = "{reader.state.validation.position}")
    @Max(value = 2000000, message = "{reader.state.validation.position}")
    private Integer characterOffset;

    @Size(max = 500, message = "{reader.state.validation.quote}")
    private String selectedText;

    @Size(max = 2000, message = "{reader.state.validation.note}")
    private String noteText;
}
