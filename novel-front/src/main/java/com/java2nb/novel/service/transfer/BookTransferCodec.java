package com.java2nb.novel.service.transfer;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

final class BookTransferCodec {
    static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    static final int MAX_UNCOMPRESSED_BYTES = 100 * 1024 * 1024;
    static final int MAX_ENTRY_BYTES = 20 * 1024 * 1024;
    static final int MAX_ARCHIVE_ENTRIES = 2_048;
    static final int MAX_CHAPTERS = 500;
    static final int MAX_CHAPTER_CHARS = 2_000_000;
    private static final String W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
        "(?iu)^\\s*(chương|chuong|chapter)\\s+([0-9ivxlcdm]+|[^\\s:.-]+).*$");
    private static final Set<String> XHTML_BLOCKS = Set.of(
        "p", "div", "section", "article", "li", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote");

    List<BookTransferChapter> parse(BookTransferFormat format, byte[] source) {
        if (source == null || source.length == 0) throw new IllegalArgumentException("Tệp nhập đang trống");
        if (source.length > MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Tệp nhập vượt quá 20 MB");
        List<BookTransferChapter> chapters = switch (format) {
            case TXT -> parseTxt(decodeText(source));
            case DOCX -> parseDocx(source);
            case EPUB -> parseEpub(source);
        };
        return validateChapters(chapters);
    }

    byte[] write(BookTransferFormat format, String bookTitle, List<BookTransferChapter> chapters) {
        List<BookTransferChapter> safeChapters = validateChapters(chapters);
        String safeTitle = normalizeTitle(bookTitle, "Tác phẩm");
        return switch (format) {
            case TXT -> writeTxt(safeChapters);
            case DOCX -> writeDocx(safeTitle, safeChapters);
            case EPUB -> writeEpub(safeTitle, safeChapters);
        };
    }

    private List<BookTransferChapter> parseTxt(String text) {
        List<BookTransferChapter> result = new ArrayList<>();
        String currentTitle = null;
        StringBuilder content = new StringBuilder();
        for (String line : normalizeLines(text).split("\\n", -1)) {
            if (CHAPTER_HEADING.matcher(line).matches()) {
                appendChapter(result, currentTitle, content);
                currentTitle = line.trim();
                content.setLength(0);
            } else {
                if (content.length() > 0) content.append('\n');
                content.append(line);
            }
        }
        appendChapter(result, currentTitle, content);
        if (result.isEmpty()) result.add(new BookTransferChapter("Chương 1", normalizeContent(text)));
        return result;
    }

    private List<BookTransferChapter> parseDocx(byte[] source) {
        Map<String, byte[]> entries = readArchive(source);
        byte[] documentXml = entries.get("word/document.xml");
        if (documentXml == null) throw new IllegalArgumentException("DOCX thiếu word/document.xml");
        Document document = parseXml(documentXml);
        NodeList paragraphs = document.getElementsByTagNameNS(W_NS, "p");
        List<BookTransferChapter> result = new ArrayList<>();
        String currentTitle = null;
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < paragraphs.getLength(); i++) {
            Element paragraph = (Element) paragraphs.item(i);
            String text = wordParagraphText(paragraph).trim();
            String style = wordParagraphStyle(paragraph);
            boolean heading = style.toLowerCase(Locale.ROOT).startsWith("heading")
                || CHAPTER_HEADING.matcher(text).matches();
            if (heading && !text.isBlank()) {
                appendChapter(result, currentTitle, content);
                currentTitle = text;
                content.setLength(0);
            } else if (!"title".equalsIgnoreCase(style)) {
                if (content.length() > 0) content.append('\n');
                content.append(text);
            }
        }
        appendChapter(result, currentTitle, content);
        if (result.isEmpty()) throw new IllegalArgumentException("DOCX không chứa nội dung chương");
        return result;
    }

    private List<BookTransferChapter> parseEpub(byte[] source) {
        Map<String, byte[]> entries = readArchive(source);
        byte[] container = entries.get("META-INF/container.xml");
        if (container == null) throw new IllegalArgumentException("EPUB thiếu META-INF/container.xml");
        Document containerXml = parseXml(container);
        Element rootFile = firstElement(containerXml, "rootfile");
        String opfPath = rootFile == null ? null : rootFile.getAttribute("full-path");
        opfPath = requireArchivePath(opfPath);
        byte[] opfBytes = entries.get(opfPath);
        if (opfBytes == null) throw new IllegalArgumentException("EPUB thiếu package document");
        Document opf = parseXml(opfBytes);
        Map<String, String> manifest = new HashMap<>();
        NodeList items = opf.getElementsByTagNameNS("*", "item");
        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            String mediaType = item.getAttribute("media-type");
            if ("application/xhtml+xml".equals(mediaType) || "text/html".equals(mediaType)) {
                manifest.put(item.getAttribute("id"), resolveArchivePath(opfPath, item.getAttribute("href")));
            }
        }
        List<BookTransferChapter> result = new ArrayList<>();
        NodeList spine = opf.getElementsByTagNameNS("*", "itemref");
        for (int i = 0; i < spine.getLength(); i++) {
            Element itemRef = (Element) spine.item(i);
            String chapterPath = manifest.get(itemRef.getAttribute("idref"));
            if (chapterPath == null) continue;
            byte[] chapterBytes = entries.get(chapterPath);
            if (chapterBytes == null) throw new IllegalArgumentException("EPUB thiếu tệp chương trong spine");
            Document xhtml = parseXml(chapterBytes);
            Element body = firstElement(xhtml, "body");
            if (body == null) continue;
            String title = elementText(firstElement(xhtml, "h1"));
            if (title.isBlank()) title = elementText(firstElement(xhtml, "title"));
            if (title.isBlank()) title = "Chương " + (result.size() + 1);
            StringBuilder content = new StringBuilder();
            for (Node child = body.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == Node.ELEMENT_NODE && "h1".equalsIgnoreCase(child.getLocalName())
                    && child.getTextContent().trim().equals(title)) {
                    continue;
                }
                appendXhtmlText(child, content);
            }
            result.add(new BookTransferChapter(title, normalizeContent(content.toString())));
            if (result.size() > MAX_CHAPTERS) throw new IllegalArgumentException("Tệp có quá 500 chương");
        }
        if (result.isEmpty()) throw new IllegalArgumentException("EPUB không có chương đọc được trong spine");
        return result;
    }

    private byte[] writeTxt(List<BookTransferChapter> chapters) {
        StringBuilder output = new StringBuilder();
        for (BookTransferChapter chapter : chapters) {
            if (output.length() > 0) output.append("\r\n\r\n");
            output.append(chapter.title()).append("\r\n\r\n")
                .append(chapter.content().replace("\n", "\r\n"));
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }

    private byte[] writeDocx(String bookTitle, List<BookTransferChapter> chapters) {
        StringBuilder body = new StringBuilder();
        long documentBytes = appendWordParagraph(body, bookTitle, "Title", 0);
        for (BookTransferChapter chapter : chapters) {
            documentBytes = appendWordParagraph(body, chapter.title(), "Heading1", documentBytes);
            for (String line : chapter.content().split("\\n", -1)) {
                documentBytes = appendWordParagraph(body, line, null, documentBytes);
            }
        }
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:document xmlns:w=\"" + W_NS + "\"><w:body>" + body
            + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
            + "<w:pgMar w:top=\"1440\" w:right=\"1440\" w:bottom=\"1440\" w:left=\"1440\"/>"
            + "</w:sectPr></w:body></w:document>";
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
            + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>"
            + "</Types>").getBytes(StandardCharsets.UTF_8));
        entries.put("_rels/.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "</Relationships>").getBytes(StandardCharsets.UTF_8));
        entries.put("word/_rels/document.xml.rels", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
            + "</Relationships>").getBytes(StandardCharsets.UTF_8));
        entries.put("word/styles.xml", wordStyles().getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", document.getBytes(StandardCharsets.UTF_8));
        return zip(entries, false);
    }

    private String wordStyles() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<w:styles xmlns:w=\"" + W_NS + "\">"
            + "<w:docDefaults><w:rPrDefault><w:rPr>"
            + "<w:rFonts w:ascii=\"Times New Roman\" w:hAnsi=\"Times New Roman\" w:eastAsia=\"Times New Roman\"/>"
            + "<w:sz w:val=\"24\"/><w:szCs w:val=\"24\"/><w:lang w:val=\"vi-VN\"/>"
            + "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr>"
            + "<w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/>"
            + "</w:pPr></w:pPrDefault></w:docDefaults>"
            + "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\">"
            + "<w:name w:val=\"Normal\"/><w:qFormat/></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Title\">"
            + "<w:name w:val=\"Title\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/>"
            + "<w:uiPriority w:val=\"10\"/><w:qFormat/><w:pPr><w:spacing w:after=\"360\"/>"
            + "<w:jc w:val=\"center\"/></w:pPr><w:rPr><w:b/><w:sz w:val=\"40\"/><w:szCs w:val=\"40\"/>"
            + "</w:rPr></w:style>"
            + "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\">"
            + "<w:name w:val=\"heading 1\"/><w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/>"
            + "<w:uiPriority w:val=\"9\"/><w:qFormat/><w:pPr><w:keepNext/>"
            + "<w:spacing w:before=\"360\" w:after=\"160\"/><w:outlineLvl w:val=\"0\"/></w:pPr>"
            + "<w:rPr><w:b/><w:sz w:val=\"32\"/><w:szCs w:val=\"32\"/></w:rPr></w:style>"
            + "</w:styles>";
    }

    private byte[] writeEpub(String bookTitle, List<BookTransferChapter> chapters) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
        entries.put("META-INF/container.xml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">"
            + "<rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>"
            + "</rootfiles></container>").getBytes(StandardCharsets.UTF_8));
        StringBuilder manifest = new StringBuilder();
        StringBuilder spine = new StringBuilder();
        StringBuilder nav = new StringBuilder();
        for (int i = 0; i < chapters.size(); i++) {
            int number = i + 1;
            BookTransferChapter chapter = chapters.get(i);
            String filename = "chapter-" + number + ".xhtml";
            manifest.append("<item id=\"c").append(number).append("\" href=\"").append(filename)
                .append("\" media-type=\"application/xhtml+xml\"/>");
            spine.append("<itemref idref=\"c").append(number).append("\"/>");
            nav.append("<li><a href=\"").append(filename).append("\">")
                .append(xml(chapter.title())).append("</a></li>");
            entries.put("OEBPS/" + filename, chapterXhtml(bookTitle, chapter).getBytes(StandardCharsets.UTF_8));
        }
        entries.put("OEBPS/nav.xhtml", ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\">"
            + "<head><title>Mục lục</title></head><body><nav epub:type=\"toc\"><ol>" + nav
            + "</ol></nav></body></html>").getBytes(StandardCharsets.UTF_8));
        String opf = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\">"
            + "<metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"book-id\">urn:uuid:"
            + UUID.nameUUIDFromBytes(bookTitle.getBytes(StandardCharsets.UTF_8)) + "</dc:identifier><dc:title>"
            + xml(bookTitle) + "</dc:title><dc:language>vi</dc:language></metadata><manifest>"
            + "<item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>"
            + manifest + "</manifest><spine>" + spine + "</spine></package>";
        entries.put("OEBPS/content.opf", opf.getBytes(StandardCharsets.UTF_8));
        return zip(entries, true);
    }

    private String chapterXhtml(String bookTitle, BookTransferChapter chapter) {
        StringBuilder paragraphs = new StringBuilder();
        for (String line : chapter.content().split("\\n", -1)) {
            paragraphs.append(line.isEmpty() ? "<p><br/></p>" : "<p>" + xml(line) + "</p>");
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\">"
            + "<head><title>" + xml(chapter.title()) + "</title><meta charset=\"UTF-8\"/></head><body>"
            + "<h1>" + xml(chapter.title()) + "</h1>" + paragraphs + "</body></html>";
    }

    private Map<String, byte[]> readArchive(byte[] source) {
        if (source.length < 4 || source[0] != 'P' || source[1] != 'K') {
            throw new IllegalArgumentException("Tệp ZIP/DOCX/EPUB không hợp lệ");
        }
        Map<String, byte[]> result = new LinkedHashMap<>();
        long total = 0;
        int count = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > MAX_ARCHIVE_ENTRIES) throw new IllegalArgumentException("Archive có quá nhiều tệp");
                String rawName = entry.getName();
                if (entry.isDirectory()) {
                    requireArchivePath(rawName.endsWith("/") ? rawName.substring(0, rawName.length() - 1) : rawName);
                    continue;
                }
                String name = requireArchivePath(rawName);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (output.size() + read > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                        throw new IllegalArgumentException("Archive giải nén vượt giới hạn an toàn");
                    }
                    output.write(buffer, 0, read);
                }
                if (result.putIfAbsent(name, output.toByteArray()) != null) {
                    throw new IllegalArgumentException("Archive chứa đường dẫn trùng lặp");
                }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Không thể đọc archive", exception);
        }
        return result;
    }

    private byte[] zip(Map<String, byte[]> entries, boolean epub) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(output)) {
            if (entries.size() > MAX_ARCHIVE_ENTRIES) {
                throw new IllegalArgumentException("Tệp xuất có quá nhiều entry");
            }
            long total = 0;
            for (Map.Entry<String, byte[]> item : entries.entrySet()) {
                String name = requireArchivePath(item.getKey());
                byte[] value = Objects.requireNonNull(item.getValue(), "Nội dung entry không được để trống");
                total += value.length;
                if (value.length > MAX_ENTRY_BYTES || total > MAX_UNCOMPRESSED_BYTES) {
                    throw new IllegalArgumentException("Tệp xuất vượt giới hạn an toàn");
                }
                ZipEntry entry = new ZipEntry(name);
                if (epub && "mimetype".equals(item.getKey())) {
                    CRC32 crc = new CRC32();
                    crc.update(value);
                    entry.setMethod(ZipEntry.STORED);
                    entry.setSize(value.length);
                    entry.setCompressedSize(value.length);
                    entry.setCrc(crc.getValue());
                }
                zip.putNextEntry(entry);
                zip.write(value);
                zip.closeEntry();
            }
            zip.finish();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo tệp xuất", exception);
        }
    }

    private Document parseXml(byte[] bytes) {
        if (bytes.length > MAX_ENTRY_BYTES) throw new IllegalArgumentException("XML vượt giới hạn an toàn");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(bytes)));
        } catch (Exception exception) {
            throw new IllegalArgumentException("XML trong tệp không hợp lệ hoặc không an toàn", exception);
        }
    }

    private List<BookTransferChapter> validateChapters(List<BookTransferChapter> chapters) {
        if (chapters == null || chapters.isEmpty()) throw new IllegalArgumentException("Không tìm thấy chương để xử lý");
        if (chapters.size() > MAX_CHAPTERS) throw new IllegalArgumentException("Tệp có quá 500 chương");
        List<BookTransferChapter> result = new ArrayList<>(chapters.size());
        long totalTextBytes = 0;
        for (int i = 0; i < chapters.size(); i++) {
            BookTransferChapter chapter = chapters.get(i);
            String title = normalizeTitle(chapter == null ? null : chapter.title(), "Chương " + (i + 1));
            String content = normalizeContent(chapter == null ? null : chapter.content());
            if (content.length() > MAX_CHAPTER_CHARS) {
                throw new IllegalArgumentException("Một chương vượt quá 2.000.000 ký tự");
            }
            totalTextBytes += title.getBytes(StandardCharsets.UTF_8).length;
            totalTextBytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (totalTextBytes > MAX_UPLOAD_BYTES) {
                throw new IllegalArgumentException("Tổng nội dung xử lý vượt quá 20 MB");
            }
            result.add(new BookTransferChapter(title, content));
        }
        return List.copyOf(result);
    }

    private void appendChapter(List<BookTransferChapter> result, String title, StringBuilder content) {
        String normalized = normalizeContent(content.toString());
        if (title == null && normalized.isBlank()) return;
        result.add(new BookTransferChapter(title == null ? "Lời mở đầu" : title, normalized));
        if (result.size() > MAX_CHAPTERS) throw new IllegalArgumentException("Tệp có quá 500 chương");
    }

    private String decodeText(byte[] bytes) {
        Charset charset = StandardCharsets.UTF_8;
        int offset = 0;
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            offset = 3;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            charset = StandardCharsets.UTF_16LE;
            offset = 2;
        } else if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            charset = StandardCharsets.UTF_16BE;
            offset = 2;
        }
        try {
            CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("Mã hóa văn bản không hợp lệ; hãy dùng UTF-8 hoặc UTF-16 có BOM", exception);
        }
    }

    private String requireArchivePath(String name) {
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0 || name.indexOf('\\') >= 0
            || name.startsWith("/") || name.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("Archive chứa đường dẫn không an toàn");
        }
        Path normalized = Path.of(name).normalize();
        String value = normalized.toString().replace('\\', '/');
        if (value.equals("..") || value.startsWith("../") || !value.equals(name)) {
            throw new IllegalArgumentException("Archive chứa path traversal");
        }
        return value;
    }

    private String resolveArchivePath(String baseFile, String href) {
        try {
            URI uri = URI.create(href);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getQuery() != null) {
                throw new IllegalArgumentException("EPUB chứa liên kết chương không an toàn");
            }
            String path = uri.getPath();
            Path base = Path.of(baseFile).getParent();
            Path resolved = (base == null ? Path.of(path) : base.resolve(path)).normalize();
            String value = resolved.toString().replace('\\', '/');
            if (value.equals("..") || value.startsWith("../")) {
                throw new IllegalArgumentException("EPUB chứa path traversal");
            }
            return requireArchivePath(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("EPUB chứa đường dẫn chương không hợp lệ", exception);
        }
    }

    private Element firstElement(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? null : (Element) nodes.item(0);
    }

    private String elementText(Element element) {
        return element == null ? "" : element.getTextContent().trim();
    }

    private String wordParagraphStyle(Element paragraph) {
        NodeList styles = paragraph.getElementsByTagNameNS(W_NS, "pStyle");
        if (styles.getLength() == 0) return "";
        Element style = (Element) styles.item(0);
        String value = style.getAttributeNS(W_NS, "val");
        return value.isBlank() ? style.getAttribute("w:val") : value;
    }

    private String wordParagraphText(Element paragraph) {
        StringBuilder text = new StringBuilder();
        appendWordText(paragraph, text);
        return text.toString();
    }

    private void appendWordText(Node node, StringBuilder output) {
        String local = node.getLocalName();
        if ("t".equals(local)) output.append(node.getTextContent());
        else if ("tab".equals(local)) output.append('\t');
        else if ("br".equals(local) || "cr".equals(local)) output.append('\n');
        else for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendWordText(child, output);
        }
    }

    private void appendXhtmlText(Node node, StringBuilder output) {
        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            output.append(node.getNodeValue());
            return;
        }
        String local = node.getLocalName() == null ? "" : node.getLocalName().toLowerCase(Locale.ROOT);
        if ("script".equals(local) || "style".equals(local) || "nav".equals(local)) return;
        if ("br".equals(local)) output.append('\n');
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            appendXhtmlText(child, output);
        }
        if (XHTML_BLOCKS.contains(local) && (output.length() == 0 || output.charAt(output.length() - 1) != '\n')) {
            output.append('\n');
        }
    }

    private String wordParagraph(String value, String style) {
        String styleXml = style == null ? "" : "<w:pPr><w:pStyle w:val=\"" + style + "\"/></w:pPr>";
        return "<w:p>" + styleXml + "<w:r><w:t xml:space=\"preserve\">" + xml(value) + "</w:t></w:r></w:p>";
    }

    private long appendWordParagraph(StringBuilder body, String value, String style, long currentBytes) {
        String paragraph = wordParagraph(value, style);
        long updatedBytes = currentBytes + paragraph.getBytes(StandardCharsets.UTF_8).length;
        if (updatedBytes > MAX_ENTRY_BYTES) {
            throw new IllegalArgumentException("DOCX xuất vượt giới hạn 20 MB cho document.xml");
        }
        body.append(paragraph);
        return updatedBytes;
    }

    private String normalizeTitle(String value, String fallback) {
        String title = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim().replaceAll(" {2,}", " ");
        if (title.isBlank()) title = fallback;
        if (title.length() > 100) title = title.substring(0, 100).trim();
        return title;
    }

    private String normalizeContent(String value) {
        String normalized = normalizeLines(value == null ? "" : value);
        return normalized.replaceAll("[ \\t]+(?=\\n|$)", "").strip();
    }

    private String normalizeLines(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    private String xml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
