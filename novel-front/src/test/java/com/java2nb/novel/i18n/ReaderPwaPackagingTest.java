package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderPwaPackagingTest {

    @Test
    void manifestServiceWorkerAndOfflineShellArePackaged() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path staticRoot = module.resolve("src/main/resources/static");
        String manifest = read(staticRoot.resolve("manifest.json"));
        String worker = read(staticRoot.resolve("service-worker.js"));

        assertThat(manifest).contains(
            "\"name\": \"Novel Plus Việt Nam\"",
            "\"lang\": \"vi\"",
            "\"display\": \"standalone\"",
            "\"start_url\": \"/?source=pwa\""
        );
        assertThat(Files.isRegularFile(staticRoot.resolve("offline-reader.htm"))).isTrue();
        assertThat(Files.isRegularFile(staticRoot.resolve("javascript/offline-reader.js"))).isTrue();

        assertThat(worker).contains(
            "CACHE_VERSION = 'v6'",
            "if (request.method !== 'GET')",
            "if (request.mode === 'navigate')",
            "fetch(request).catch",
            "if (!isPublicAsset(url.pathname))",
            "fetch(request, {cache: 'no-cache'})",
            "cacheControl.indexOf('private')",
            "cacheControl.indexOf('no-store')",
            "!response.headers.get('Set-Cookie')"
        ).doesNotContain("return cached || network;");
        assertThat(worker.indexOf("if (request.mode === 'navigate')"))
            .isLessThan(worker.indexOf("if (!isPublicAsset(url.pathname))"));
        assertThat(worker.indexOf("fetch(request, {cache: 'no-cache'})"))
            .isLessThan(worker.lastIndexOf("caches.match(request)"));
    }

    @Test
    void chapterResponsesArePrivateAndOfflineSnapshotsAreFreeOnly() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String controller = read(module.resolve(
            "src/main/java/com/java2nb/novel/controller/page/PageController.java"));
        String webConfig = read(module.resolve(
            "src/main/java/com/java2nb/novel/core/config/WebMvcConfig.java"));
        String readerTools = read(module.resolve("src/main/resources/static/javascript/reader-tools.js"));

        assertThat(controller).contains(
            "Cache-Control\", \"private, no-store, max-age=0",
            "response.addHeader(\"Vary\", \"Cookie\")",
            "response.addHeader(\"Vary\", \"Authorization\")"
        );
        assertThat(webConfig).contains(
            "no-cache, no-store, must-revalidate",
            ".addPathPatterns(\"/service-worker.js\", \"/manifest.json\")"
        );
        assertThat(readerTools).contains(
            "document.getElementById('offlineEligible')",
            "document.getElementById('readerContentAvailable')",
            "eligibility.value !== 'true'",
            "new Error('FREE_ONLY')",
            "contentRoot.textContent",
            "utterance.lang = 'vi-VN'",
            "speech.cancel()",
            "role', 'status'",
            "aria-live",
            "FONT_FAMILY_KEY",
            "BACKGROUND_KEY",
            "aria-keyshortcuts",
            "aria-controls",
            "aria-expanded"
        ).doesNotContain("contentRoot.innerHTML");

        for (Path template : readerTemplates(module, repository)) {
            String html = read(template);
            assertThat(html)
                .as("reader template %s", template)
                .contains(
                    "id=\"offlineEligible\"",
                    "th:if=\"${offlineEligible}\"",
                    "id=\"readerContentAvailable\"",
                    "th:if=\"${!needBuy}\"",
                    "/javascript/reader-tools.js"
                )
                .doesNotContain("speechRate:0.5", "function speakChapter");
        }
    }

    @Test
    void readerAppearanceAndMobileActionsAreKeyboardAccessible() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String readerTools = read(module.resolve("src/main/resources/static/javascript/reader-tools.js"));

        assertThat(readerTools).contains(
            "\"Segoe UI\"",
            "\"Times New Roman\"",
            "readerFontSystem",
            "readerThemeSepia",
            "readerAppearanceDefault",
            "contentRoot.style.colorScheme"
        );

        for (Path template : new Path[]{
            module.resolve("src/main/resources/templates/mobile/book/book_content.html"),
            repository.resolve("templates/green/html/mobile/book/book_content.html"),
            repository.resolve("templates/orange/html/mobile/book/book_content.html"),
            repository.resolve("templates/dark/html/mobile/book/book_content.html"),
            repository.resolve("templates/dark/html/book/book_content.html")
        }) {
            assertThat(read(template)).as("Accessible reader controls in %s", template)
                .contains(
                    "<button type=\"button\" id=\"lightdiv\"",
                    "<button type=\"button\" id=\"fontbig\"",
                    "th:aria-label=\"#{reader.back}\"",
                    "th:aria-label=\"#{site.home}\"",
                    "aria-hidden=\"true\"",
                    "/javascript/reader-tools.js?v=7"
                )
                .doesNotContain("maximum-scale", "<a id=\"lightdiv\"", "<a id=\"fontbig\"");
        }
    }

    @Test
    void allRuntimeAndThemeFragmentsRegisterPwaAndDataSaverDisablesAnalytics() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();

        for (Path fragment : new Path[]{
            module.resolve("src/main/resources/templates/common/js.html"),
            repository.resolve("templates/green/html/common/js.html"),
            repository.resolve("templates/orange/html/common/js.html"),
            repository.resolve("templates/dark/html/common/js.html"),
            repository.resolve("templates/blue/html/common/js.html"),
            module.resolve("src/main/resources/templates/mobile/common/js.html"),
            repository.resolve("templates/green/html/mobile/common/js.html"),
            repository.resolve("templates/orange/html/mobile/common/js.html"),
            repository.resolve("templates/dark/html/mobile/common/js.html")
        }) {
            assertThat(read(fragment))
                .as("PWA fragment %s", fragment)
                .contains("/javascript/pwa-register.js", "readerOfflineSave", "readerTtsVoice");
        }

        assertThat(read(module.resolve("src/main/resources/static/javascript/reader-analytics.js")))
            .contains("window.NovelPwa.isDataSaverEnabled", "navigator.connection.saveData");
        assertThat(read(module.resolve("src/main/resources/static/javascript/pwa-register.js")))
            .contains(
                "navigator.serviceWorker.register('/service-worker.js'",
                "window.location.protocol !== 'https:'",
                "novel:data-saver:v1"
            );

        for (Path readerSettings : new Path[]{
            module.resolve("src/main/resources/static/mobile/js/read.js"),
            repository.resolve("templates/green/static/mobile/js/read.js"),
            repository.resolve("templates/orange/static/mobile/js/read.js"),
            repository.resolve("templates/dark/static/js/read.js"),
            repository.resolve("templates/dark/static/mobile/js/read.js")
        }) {
            assertThat(read(readerSettings))
                .as("reader settings %s", readerSettings)
                .contains("if (!nr_body || !nr1)");
        }
        assertThat(read(repository.resolve("templates/dark/html/book/book_content.html")))
            .contains("booksArr.splice(existingIndex, 1)")
            .doesNotContain("booksArr.remove(");
    }

    @Test
    void commonJavascriptReferencesAreVersionedForStaleWorkerUpgrade() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        int versionedReferences = 0;

        for (Path root : new Path[]{
            module.resolve("src/main/resources/templates"),
            repository.resolve("templates")
        }) {
            List<Path> htmlFiles;
            try (Stream<Path> paths = Files.walk(root)) {
                htmlFiles = paths.filter(path -> path.toString().endsWith(".html")).toList();
            }
            for (Path htmlFile : htmlFiles) {
                String html = read(htmlFile);
                assertThat(html)
                    .as("Common JavaScript URL in %s", htmlFile)
                    .doesNotContain("src=\"/javascript/common.js\"");
                if (html.contains("src=\"/javascript/common.js?v=5\"")) {
                    versionedReferences++;
                }
            }
        }

        assertThat(versionedReferences).isEqualTo(27);
    }

    private Path[] readerTemplates(Path module, Path repository) {
        return new Path[]{
            module.resolve("src/main/resources/templates/book/book_content.html"),
            repository.resolve("templates/green/html/book/book_content.html"),
            repository.resolve("templates/orange/html/book/book_content.html"),
            repository.resolve("templates/dark/html/book/book_content.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_content.html"),
            repository.resolve("templates/green/html/mobile/book/book_content.html"),
            repository.resolve("templates/orange/html/mobile/book/book_content.html"),
            repository.resolve("templates/dark/html/mobile/book/book_content.html")
        };
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
