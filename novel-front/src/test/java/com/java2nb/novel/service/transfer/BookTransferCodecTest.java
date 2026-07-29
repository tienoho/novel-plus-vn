package com.java2nb.novel.service.transfer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookTransferCodecTest {
    private final BookTransferCodec codec = new BookTransferCodec();
    private final List<BookTransferChapter> chapters = List.of(
        new BookTransferChapter("Chương 1: Khởi đầu", "Dòng một\nDòng hai có tiếng Việt."),
        new BookTransferChapter("Chương 2", "Nội dung <không phải thẻ> & ký hiệu."));

    @Test
    void txtSupportsVietnameseHeadingsAndUtf16Bom() {
        String text = "Chương 1: Mở đầu\r\nXin chào\r\nChuong 2\r\nKết thúc";
        byte[] utf16 = text.getBytes(StandardCharsets.UTF_16LE);
        byte[] withBom = new byte[utf16.length + 2];
        withBom[0] = (byte) 0xFF;
        withBom[1] = (byte) 0xFE;
        System.arraycopy(utf16, 0, withBom, 2, utf16.length);

        List<BookTransferChapter> parsed = codec.parse(BookTransferFormat.TXT, withBom);

        assertThat(parsed).hasSize(2);
        assertThat(parsed.get(0).title()).isEqualTo("Chương 1: Mở đầu");
        assertThat(parsed.get(1).content()).isEqualTo("Kết thúc");
    }

    @Test
    void txtDocxAndEpubRoundTripChapterOrderAndUnicode() {
        for (BookTransferFormat format : BookTransferFormat.values()) {
            byte[] exported = codec.write(format, "Truyện Việt", chapters);
            List<BookTransferChapter> imported = codec.parse(format, exported);

            assertThat(imported).as("Round-trip %s", format).containsExactlyElementsOf(chapters);
        }
    }

    @Test
    void archiveRejectsPathTraversalAndDuplicateEntries() throws Exception {
        byte[] traversal = zip(Map.of("../word/document.xml", "x".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> codec.parse(BookTransferFormat.DOCX, traversal))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("traversal");

        byte[] duplicate = zipDuplicate("word/document.xml");
        assertThatThrownBy(() -> codec.parse(BookTransferFormat.DOCX, duplicate))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("trùng lặp");
    }

    @Test
    void docxRejectsDoctypeAndExternalEntity() throws Exception {
        String malicious = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY steal SYSTEM \"file:///etc/passwd\">]>"
            + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
            + "<w:body><w:p><w:r><w:t>&steal;</w:t></w:r></w:p></w:body></w:document>";
        byte[] docx = zip(Map.of("word/document.xml", malicious.getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> codec.parse(BookTransferFormat.DOCX, docx))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không an toàn");
    }

    @Test
    void compressedEntryCannotExpandPastPerEntryLimit() throws Exception {
        byte[] oversized = new byte[BookTransferCodec.MAX_ENTRY_BYTES + 1];
        byte[] bomb = zip(Map.of("word/document.xml", oversized));

        assertThat(bomb.length).isLessThan(100_000);
        assertThatThrownBy(() -> codec.parse(BookTransferFormat.DOCX, bomb))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("giới hạn an toàn");
    }

    @Test
    void invalidUtf8DoesNotSilentlyReplaceCharacters() {
        assertThatThrownBy(() -> codec.parse(BookTransferFormat.TXT, new byte[]{(byte) 0xC3, 0x28}))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Mã hóa");
    }

    @Test
    void docxDefinesTitleAndHeadingStylesThroughAValidRelationship() throws Exception {
        Map<String, byte[]> entries = unzip(codec.write(BookTransferFormat.DOCX, "Truyện Việt", chapters));

        assertThat(entries).containsKeys("[Content_Types].xml", "word/document.xml",
            "word/styles.xml", "word/_rels/document.xml.rels");
        assertThat(new String(entries.get("[Content_Types].xml"), StandardCharsets.UTF_8))
            .contains("/word/styles.xml", "wordprocessingml.styles+xml");
        assertThat(new String(entries.get("word/_rels/document.xml.rels"), StandardCharsets.UTF_8))
            .contains("relationships/styles", "Target=\"styles.xml\"");
        assertThat(new String(entries.get("word/styles.xml"), StandardCharsets.UTF_8))
            .contains("w:styleId=\"Title\"", "w:styleId=\"Heading1\"", "w:val=\"vi-VN\"");
        assertThat(new String(entries.get("word/document.xml"), StandardCharsets.UTF_8))
            .contains("w:pStyle w:val=\"Title\"", "w:pStyle w:val=\"Heading1\"");
    }

    @Test
    void epubWritesStoredMimetypeFirstAndOpf3SpineInChapterOrder() throws Exception {
        byte[] epub = codec.write(BookTransferFormat.EPUB, "Truyện Việt", chapters);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(epub))) {
            ZipEntry first = zip.getNextEntry();
            assertThat(first.getName()).isEqualTo("mimetype");
            assertThat(first.getMethod()).isEqualTo(ZipEntry.STORED);
            assertThat(new String(zip.readAllBytes(), StandardCharsets.US_ASCII))
                .isEqualTo("application/epub+zip");
        }
        Map<String, byte[]> entries = unzip(epub);
        String opf = new String(entries.get("OEBPS/content.opf"), StandardCharsets.UTF_8);
        assertThat(opf).contains("version=\"3.0\"", "properties=\"nav\"")
            .containsSubsequence("<itemref idref=\"c1\"/>", "<itemref idref=\"c2\"/>");
    }

    @Test
    void exportRejectsAggregateTextPastTwentyMegabytes() {
        String block = "a".repeat(50_000);
        List<BookTransferChapter> oversized = new ArrayList<>();
        for (int i = 1; i <= BookTransferCodec.MAX_CHAPTERS; i++) {
            oversized.add(new BookTransferChapter("Chương " + i, block));
        }

        assertThatThrownBy(() -> codec.write(BookTransferFormat.TXT, "Tác phẩm", oversized))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("20 MB");
    }

    private byte[] zip(Map<String, byte[]> entries) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(item.getKey()));
                zip.write(item.getValue());
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        }
    }

    private byte[] zipDuplicate(String name) throws Exception {
        String alias = name.substring(0, name.length() - 1) + "x";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(name, new byte[]{'a'});
        entries.put(alias, new byte[]{'b'});
        byte[] archive = zip(entries);
        byte[] source = alias.getBytes(StandardCharsets.UTF_8);
        byte[] replacement = name.getBytes(StandardCharsets.UTF_8);
        int replacements = 0;
        for (int i = 0; i <= archive.length - source.length; i++) {
            boolean matches = true;
            for (int j = 0; j < source.length; j++) {
                if (archive[i + j] != source[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                System.arraycopy(replacement, 0, archive, i, replacement.length);
                replacements++;
                i += source.length - 1;
            }
        }
        assertThat(replacements).isEqualTo(2);
        return archive;
    }

    private Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }
}
