package com.java2nb.common.security;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminClientSecurityPackagingTest {

    private final Path module = Path.of("").toAbsolutePath().normalize();

    @Test
    void dictionaryRendererUsesTextNodesAndValidatedFunctionLookup() throws Exception {
        String script = read("src/main/resources/static/js/dict-util.js");

        assertThat(script)
            .contains("document.createElement(\"option\")")
            .contains("option.textContent =")
            .contains("/^[A-Za-z_$][A-Za-z0-9_$]*$/")
            .contains("typeof handler === \"function\"")
            .doesNotContain("eval(", ".append(html)", "innerHTML");
    }

    @Test
    void htmlDecoderDoesNotParseUntrustedMarkup() throws Exception {
        String script = read("src/main/resources/static/js/common.js");

        assertThat(script)
            .contains("String.fromCodePoint(codePoint)")
            .contains("codePoint > 0x10FFFF")
            .doesNotContain("temp.innerHTML = text");
    }

    @Test
    void myBatisMappersDoNotUseRawStringSubstitution() throws Exception {
        Path mapperRoot = module.resolve("src/main/resources/mybatis");
        List<String> unsafe;
        try (var paths = Files.walk(mapperRoot)) {
            unsafe = paths.filter(path -> path.toString().endsWith(".xml"))
                .filter(path -> {
                    try {
                        return Files.readString(path, StandardCharsets.UTF_8).contains("${");
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .map(mapperRoot::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        }

        assertThat(unsafe).isEmpty();
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(module.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
