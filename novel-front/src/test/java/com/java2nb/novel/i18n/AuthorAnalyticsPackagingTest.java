package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorAnalyticsPackagingTest {

    @Test
    void readerTrackingIsPresentInRuntimeAndThemeOverlays() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String runtime = read(module.resolve("src/main/resources/templates/book/book_content.html"));

        assertThat(runtime).contains("analyticsEnabled", "/javascript/reader-analytics.js");
        assertThat(read(repository.resolve("templates/green/html/book/book_content.html"))).isEqualTo(runtime);
        assertThat(read(repository.resolve("templates/orange/html/book/book_content.html"))).isEqualTo(runtime);
    }

    @Test
    void dashboardAndNavigationArePackaged() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        assertThat(read(module.resolve("src/main/resources/templates/author/author_analytics.html")))
            .contains("/author/analytics/summary", "/author/analytics/chapters", "author.analytics.retention.note");

        for (Path index : new Path[]{
            module.resolve("src/main/resources/templates/author/index.html"),
            repository.resolve("templates/green/html/author/index.html"),
            repository.resolve("templates/orange/html/author/index.html")
        }) {
            assertThat(read(index)).contains("/author/author_analytics.html?bookId=");
        }
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
