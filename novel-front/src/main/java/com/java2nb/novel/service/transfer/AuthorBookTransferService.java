package com.java2nb.novel.service.transfer;

public interface AuthorBookTransferService {
    BookImportResult importBook(long authorId, long bookId, String filename, byte[] source);

    BookExportFile exportBook(long authorId, long bookId, BookTransferFormat format);
}
