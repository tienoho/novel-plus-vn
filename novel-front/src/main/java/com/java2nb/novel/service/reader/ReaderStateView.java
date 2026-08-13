package com.java2nb.novel.service.reader;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReaderStateView {
    private ReaderProgressRow progress;
    private List<ReaderAnnotationRow> annotations;
}
