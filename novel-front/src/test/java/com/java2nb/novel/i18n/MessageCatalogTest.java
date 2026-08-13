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

class MessageCatalogTest {

    private static final Pattern MESSAGE_KEY = Pattern.compile("^([^#!\\s][^=]*)=");
    private static final Pattern HAN_TEXT = Pattern.compile("[\\u4E00-\\u9FA5]+");
    private static final Map<String, AllowedHan> TEMPLATE_HAN_ALLOWLIST = Map.of(
        "author/book_add.html", new AllowedHan(Set.of("玄幻奇幻"),
            "Tên category mặc định được gửi về backend qua input ẩn catName; không phải nhãn hiển thị.")
    );
    private static final Set<String> THIRD_PARTY_CHINESE_JS = Set.of(
        "javascript/easyui-lang-zh_CN.js",
        "javascript/layer.m.js"
    );
    private static final Map<String, AllowedHan> JAVA_HAN_ALLOWLIST = Map.of(
        "com/java2nb/novel/service/impl/IpLocationServiceImpl.java",
        new AllowedHan(Set.of(
            "北京市", "天津市", "上海市", "重庆市", "河北省", "山西省", "辽宁省", "吉林省",
            "黑龙江省", "江苏省", "浙江省", "安徽省", "福建省", "江西省", "山东省", "河南省",
            "湖北省", "湖南省", "广东省", "海南省", "四川省", "贵州省", "云南省", "陕西省",
            "甘肃省", "青海省", "台湾省", "内蒙古自治区", "广西壮族自治区", "西藏自治区",
            "宁夏回族自治区", "新疆维吾尔自治区", "香港", "香港特别行政区", "澳门", "澳门特别行政区", "中国"
        ), "Giá trị contract do cơ sở dữ liệu ip2region trả về; chỉ dùng làm khóa để ánh xạ sang tiếng Việt.")
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
    void templatesContainOnlyDocumentedChineseContractValues() throws Exception {
        URL resource = getClass().getClassLoader().getResource("templates");
        assertNotNull(resource, "templates");
        Path root = Paths.get(resource.toURI());

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".html")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                AllowedHan exception = TEMPLATE_HAN_ALLOWLIST.get(relative);
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

    @Test
    void firstPartyStaticJavaScriptDoesNotContainChineseRuntimeText() throws Exception {
        URL resource = getClass().getClassLoader().getResource("static");
        assertNotNull(resource, "static");
        Path root = Paths.get(resource.toURI());

        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".js")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("layui/")
                    || relative.startsWith("mobile/layui/")
                    || THIRD_PARTY_CHINESE_JS.contains(relative)) {
                    continue;
                }
                Matcher matcher = HAN_TEXT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                assertFalse(matcher.find(),
                    () -> "Chuỗi Trung trong JavaScript first-party: " + relative + " -> " + matcher.group());
            }
        }
    }

    @Test
    void firstPartyJavaContainsOnlyDocumentedChineseIntegrationTokens() throws Exception {
        Path root = sourceRoot("novel-front");
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                AllowedHan exception = JAVA_HAN_ALLOWLIST.get(relative);
                Set<String> allowed = exception == null ? Set.of() : exception.values();
                if (exception != null) {
                    assertFalse(exception.reason().isBlank(), "Ngoại lệ phải có lý do: " + relative);
                }
                Matcher matcher = HAN_TEXT.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    assertFalse(!allowed.contains(matcher.group()),
                        () -> "Chuỗi Trung trong Java first-party: " + relative + " -> " + matcher.group());
                }
            }
        }
    }

    private Path sourceRoot(String module) {
        Path local = Paths.get("src/main/java");
        return Files.isDirectory(local) ? local : Paths.get(module, "src/main/java");
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
