package com.java2nb.novel.service.transfer;

import java.util.List;

public record BookImportResult(int chapterCount, List<Long> draftIds, List<String> chapterNames) {
}
