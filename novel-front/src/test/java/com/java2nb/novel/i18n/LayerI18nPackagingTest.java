package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LayerI18nPackagingTest {

    @Test
    void everyDesktopAndMobileLayoutLoadsLocalizedLayerDefaults() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        Path[] layouts = {
            module.resolve("src/main/resources/templates/common/js.html"),
            module.resolve("src/main/resources/templates/mobile/common/js.html"),
            repository.resolve("templates/blue/html/common/js.html"),
            repository.resolve("templates/dark/html/common/js.html"),
            repository.resolve("templates/dark/html/mobile/common/js.html"),
            repository.resolve("templates/green/html/common/js.html"),
            repository.resolve("templates/green/html/mobile/common/js.html"),
            repository.resolve("templates/orange/html/common/js.html"),
            repository.resolve("templates/orange/html/mobile/common/js.html")
        };

        for (Path layout : layouts) {
            String html = Files.readString(layout, StandardCharsets.UTF_8);
            assertThat(html)
                .as(layout.toString())
                .contains("notice: /*[[#{common.notice}]]*/ 'Thông báo'")
                .contains("cancel: /*[[#{common.cancel}]]*/ 'Hủy'")
                .contains("/javascript/layer-i18n.js");
        }

        String adapter = Files.readString(
            module.resolve("src/main/resources/static/javascript/layer-i18n.js"), StandardCharsets.UTF_8);
        assertThat(adapter)
            .contains("layerApi.alert", "layerApi.confirm", "messages.notice", "messages.confirm", "messages.cancel");
    }
}
