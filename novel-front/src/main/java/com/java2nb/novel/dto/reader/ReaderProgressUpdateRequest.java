package com.java2nb.novel.dto.reader;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ReaderProgressUpdateRequest {
    @NotNull(message = "{reader.state.validation.book}")
    private Long bookId;

    @NotNull(message = "{reader.state.validation.chapter}")
    private Long bookIndexId;

    @NotNull(message = "{reader.state.validation.position}")
    @Min(value = 0, message = "{reader.state.validation.position}")
    @Max(value = 1000000, message = "{reader.state.validation.position}")
    private Integer paragraphIndex;

    @NotNull(message = "{reader.state.validation.position}")
    @Min(value = 0, message = "{reader.state.validation.position}")
    @Max(value = 2000000, message = "{reader.state.validation.position}")
    private Integer characterOffset;

    @NotNull(message = "{reader.state.validation.percent}")
    @DecimalMin(value = "0.00", message = "{reader.state.validation.percent}")
    @DecimalMax(value = "100.00", message = "{reader.state.validation.percent}")
    private BigDecimal progressPercent;
}
