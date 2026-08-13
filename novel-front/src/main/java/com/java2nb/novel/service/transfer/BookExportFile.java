package com.java2nb.novel.service.transfer;

public record BookExportFile(String filename, String contentType, byte[] content) {
}
