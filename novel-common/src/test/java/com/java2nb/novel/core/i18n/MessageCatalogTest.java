package com.java2nb.novel.core.i18n;

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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageCatalogTest {

    private static final Pattern MESSAGE_KEY = Pattern.compile("^([^#!\\s][^=]*)=");
    private static final Pattern HAN_TEXT = Pattern.compile("[\\u4E00-\\u9FA5]+");

    @Test
    void vietnameseAndChineseCatalogsHaveTheSameKeys() throws IOException {
        Properties vietnamese = load("i18n/common/messages_vi_VN.properties");
        Properties chinese = load("i18n/common/messages_zh_CN.properties");
        assertEquals(chinese.stringPropertyNames(), vietnamese.stringPropertyNames());
    }

    @Test
    void messageCatalogsDoNotContainDuplicateKeys() throws Exception {
        assertNoDuplicateKeys("i18n/common/messages_vi_VN.properties");
        assertNoDuplicateKeys("i18n/common/messages_zh_CN.properties");
    }

    @Test
    void firstPartyJavaDoesNotContainChineseText() throws Exception {
        Path local = Paths.get("src/main/java");
        Path root = Files.isDirectory(local) ? local : Paths.get("novel-common", "src/main/java");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = HAN_TEXT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                assertFalse(matcher.find(), () -> "Chuỗi Trung trong Java first-party: "
                    + root.relativize(file).toString().replace('\\', '/') + " -> " + matcher.group());
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
