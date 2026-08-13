package com.java2nb.novel.dto.reader;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReaderAnnotationUpdateRequest {
    @Size(max = 2000, message = "{reader.state.validation.note}")
    private String noteText;

    @NotNull(message = "{reader.state.validation.version}")
    @Min(value = 0, message = "{reader.state.validation.version}")
    private Long expectedVersion;
}
