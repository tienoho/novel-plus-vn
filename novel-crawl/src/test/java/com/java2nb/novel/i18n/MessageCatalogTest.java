package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCatalogTest {

    private static final Pattern MESSAGE_KEY = Pattern.compile("^([^#!\\s][^=]*)=");
    private static final Pattern HAN_TEXT = Pattern.compile("[\\u4E00-\\u9FA5]+");
    private static final Set<String> RULE_TOKENS = Set.of("作者", "状态", "连载", "完结", "分", "更新");
    private static final Map<String, AllowedHan> TEMPLATE_ALLOWLIST = Map.of(
        "crawl/crawlSource_add.html", new AllowedHan(RULE_TOKENS,
            "Token trong regex và giá trị trạng thái mẫu của website nguồn Trung Quốc."),
        "crawl/crawlSource_update.html", new AllowedHan(RULE_TOKENS,
            "Token trong regex và giá trị trạng thái mẫu của website nguồn Trung Quốc.")
    );
    private static final Map<String, AllowedHan> JAVA_ALLOWLIST = Map.of(
        "com/java2nb/novel/core/crawl/CrawlParser.java",
        new AllowedHan(Set.of("正在手打中"), "Dấu hiệu nội dung chưa hoàn tất trên website nguồn.")
    );

    private record AllowedHan(Set<String> values, String reason) {
    }

    @Test
    void vietnameseAndChineseCatalogsHaveTheSameKeys() throws IOException {
        Properties vietnamese = load("i18n/messages_vi_VN.properties");
        Properties chinese = load("i18n/messages_zh_CN.properties");
        assertEquals(chinese.stringPropertyNames(), vietnamese.stringPropertyNames());
    }

    @Test
    void messageCatalogsDoNotContainDuplicateKeys() throws Exception {
        assertNoDuplicateKeys("i18n/messages_vi_VN.properties");
        assertNoDuplicateKeys("i18n/messages_zh_CN.properties");
    }

    @Test
    void templatesContainOnlyDocumentedSourceRuleTokens() throws Exception {
        URL resource = getClass().getClassLoader().getResource("templates");
        assertNotNull(resource, "templates");
        scan(Paths.get(resource.toURI()), TEMPLATE_ALLOWLIST, ".html");
    }

    @Test
    void javaContainsOnlyDocumentedContractOrSourceTokens() throws Exception {
        scan(Path.of("src/main/java"), JAVA_ALLOWLIST, ".java");
    }

    @Test
    void crawlRuleTestUsesNeutralResponseKeysEndToEnd() throws Exception {
        String controller = Files.readString(
            Path.of("src/main/java/com/java2nb/novel/controller/CrawlController.java"),
            StandardCharsets.UTF_8);
        String template = Files.readString(
            Path.of("src/main/resources/templates/crawl/crawlSource_test.html"),
            StandardCharsets.UTF_8);

        assertTrue(controller.contains("resultMap.put(\"matched\", isFind)"));
        assertTrue(controller.contains("resultMap.put(\"matchResult\", matcher.group(1))"));
        assertTrue(template.contains("data.data.matched"));
        assertTrue(template.contains("data.data.matchResult"));
    }

    private void scan(Path root, Map<String, AllowedHan> allowlist, String extension) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(extension)).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                AllowedHan exception = allowlist.get(relative);
                Set<String> allowed = exception == null ? Set.of() : exception.values();
                if (exception != null) {
                    assertFalse(exception.reason().isBlank(), "Ngoại lệ phải có lý do: " + relative);
                }
                Matcher matcher = HAN_TEXT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    assertFalse(!allowed.contains(matcher.group()),
                        () -> "Chuỗi Trung chưa có trong allowlist: " + relative + " -> " + matcher.group());
                }
            }
        }
    }

    private Properties load(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            properties.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        }
        return properties;
    }

    private void assertNoDuplicateKeys(String path) throws Exception {
        URL resource = getClass().getClassLoader().getResource(path);
        assertNotNull(resource, path);
        List<String> lines = Files.readAllLines(Paths.get(resource.toURI()), StandardCharsets.UTF_8);
        Set<String> keys = new HashSet<>();
        for (String line : lines) {
            Matcher matcher = MESSAGE_KEY.matcher(line);
            if (matcher.find()) {
                assertFalse(!keys.add(matcher.group(1).trim()),
                    () -> "Key trùng trong " + path + ": " + matcher.group(1).trim());
            }
        }
    }
}
