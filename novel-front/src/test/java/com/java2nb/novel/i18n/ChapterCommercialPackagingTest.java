package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterCommercialPackagingTest {

    @Test
    void migrationAndComposePackagePolicyAfterDraftSchema() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String migration = read(repository.resolve("doc/sql/20260728_chapter_commercial_policy.sql"));
        String flywayImage = read(repository.resolve("deploy/flyway/Dockerfile"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `chapter_commercial_policy`")
            .contains("`free_from` < `free_until`")
            .contains("FOREIGN KEY (`book_index_id`) REFERENCES `book_index` (`id`) ON DELETE CASCADE")
            .contains("column_name = 'book_price'", "column_name = 'free_until'")
            .doesNotContain("DROP TABLE");
        assertThat(flywayImage.indexOf("/flyway/sql/V2026072801__author_ai.sql"))
            .isLessThan(flywayImage.indexOf(
                "/flyway/sql/V2026072802__chapter_commercial_policy.sql"));
    }

    @Test
    void authorFormsShareCommercialStateAndDarkBlueUseRuntimeFallback() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        for (Path page : new Path[]{
            module.resolve("src/main/resources/templates/author/content_add.html"),
            module.resolve("src/main/resources/templates/author/content_update.html"),
            repository.resolve("templates/green/html/author/content_add.html"),
            repository.resolve("templates/green/html/author/content_update.html"),
            repository.resolve("templates/orange/html/author/content_add.html"),
            repository.resolve("templates/orange/html/author/content_update.html")
        }) {
            assertThat(read(page)).as("Chính sách thương mại trong %s", page)
                .contains("chapterCustomPrice", "chapterUnlockAt", "chapterFreeFrom", "chapterFreeUntil")
                .contains("commercialPanelSelector", "pricePreviewSelector")
                .contains("/css/author-editor.css?v=1");
        }
        assertThat(repository.resolve("templates/dark/html/author/content_add.html")).doesNotExist();
        assertThat(repository.resolve("templates/blue/html/author/content_add.html")).doesNotExist();
    }

    @Test
    void everyReaderOverrideUsesCentralOfflineDecision() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        for (Path reader : new Path[]{
            module.resolve("src/main/resources/templates/book/book_content.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_content.html"),
            repository.resolve("templates/green/html/book/book_content.html"),
            repository.resolve("templates/green/html/mobile/book/book_content.html"),
            repository.resolve("templates/orange/html/book/book_content.html"),
            repository.resolve("templates/orange/html/mobile/book/book_content.html"),
            repository.resolve("templates/dark/html/book/book_content.html"),
            repository.resolve("templates/dark/html/mobile/book/book_content.html")
        }) {
            assertThat(read(reader)).as("Quyết định offline trong %s", reader)
                .contains("th:if=\"${offlineEligible}\"")
                .doesNotContain("bookIndex.isVip == null || bookIndex.isVip != 1");
        }
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
