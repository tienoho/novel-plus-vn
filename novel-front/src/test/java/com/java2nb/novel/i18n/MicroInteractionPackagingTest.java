package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MicroInteractionPackagingTest {

    @Test
    void sharedAssetsProvideAccessibleMotionAndFeedbackContracts() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path staticRoot = module.resolve("src/main/resources/static");
        String css = Files.readString(
            staticRoot.resolve("css/micro-interactions.css"), StandardCharsets.UTF_8);
        String javascript = Files.readString(
            staticRoot.resolve("javascript/micro-interactions.js"), StandardCharsets.UTF_8);

        assertThat(css)
            .contains("prefers-reduced-motion", ":focus-visible", "touch-action: manipulation")
            .doesNotContain("transition: all");
        assertThat(javascript)
            .contains("window.NovelUX", "aria-live", "aria-busy", "textContent",
                "MutationObserver", "pageshow")
            .doesNotContain("innerHTML = message", "innerHTML = options");
    }

    @Test
    void everyDesktopAndMobileLayoutLoadsSharedMicroInteractions() throws Exception {
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
                .contains("/css/micro-interactions.css?v=1")
                .contains("/javascript/micro-interactions.js?v=1")
                .contains("processing: /*[[#{common.processingShort}]]*/ 'Đang xử lý…'")
                .contains("close: /*[[#{common.close}]]*/ 'Đóng'");
        }
    }

    @Test
    void backToTopRespectsReducedMotionPreference() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String javascript = Files.readString(
            module.resolve("src/main/resources/static/javascript/back-to-top.js"),
            StandardCharsets.UTF_8);

        assertThat(javascript)
            .contains("prefersReducedMotion", "prefers-reduced-motion: reduce")
            .contains("behavior: reducedMotion ? 'auto' : 'smooth'");
    }
}
