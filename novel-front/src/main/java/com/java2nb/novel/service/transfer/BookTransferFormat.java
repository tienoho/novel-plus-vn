package com.java2nb.novel.service.transfer;

import java.util.Locale;

public enum BookTransferFormat {
    TXT("txt", "text/plain; charset=UTF-8"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    EPUB("epub", "application/epub+zip");

    private final String extension;
    private final String contentType;

    BookTransferFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    public static BookTransferFormat parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Thiếu định dạng nhập hoặc xuất");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith(".")) normalized = normalized.substring(1);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Chỉ hỗ trợ TXT, DOCX hoặc EPUB", exception);
        }
    }

    public static BookTransferFormat fromFilename(String filename) {
        if (filename == null) throw new IllegalArgumentException("Tên tệp nhập không hợp lệ");
        int dot = filename.lastIndexOf('.');
        return parse(dot < 0 ? "" : filename.substring(dot + 1));
    }
}
