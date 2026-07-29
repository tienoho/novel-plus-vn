package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderStatePackagingTest {

    @Test
    void migrationComposeAndMapperProtectPrivateReaderState() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String migration = read(repository.resolve("doc/sql/20260727_reader_annotations.sql"));
        String mapper = read(repository.resolve(
            "novel-front/src/main/resources/mybatis/mapping/ReaderStateMapper.xml"));
        String compose = read(repository.resolve("compose.yaml"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `reader_progress`")
            .contains("CREATE TABLE IF NOT EXISTS `reader_annotation`")
            .contains("PRIMARY KEY (`user_id`, `book_id`)")
            .contains("'BOOKMARK', 'NOTE'")
            .doesNotContain("DROP TABLE");
        assertThat(mapper)
            .contains("WHERE id = #{annotationId} AND user_id = #{userId}")
            .contains("version = #{expectedVersion}")
            .contains("ON DUPLICATE KEY UPDATE");
        assertThat(compose).contains("/migrations/20260727_reader_annotations.sql");
    }

    @Test
    void controllerKeepsReaderStateUnderAuthenticatedUserNamespace() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String controller = read(module.resolve(
            "src/main/java/com/java2nb/novel/controller/ReaderStateController.java"));

        assertThat(controller)
            .contains("@RequestMapping(\"user/reader-state\")")
            .contains("requireUser(request).getId()")
            .contains("@PutMapping(\"progress\")")
            .contains("@PostMapping(\"annotations\")")
            .contains("@DeleteMapping(\"annotations/{annotationId}\")");
    }

    @Test
    void sharedReaderToolsPackageSyncBookmarksAndNotesAcrossThemes() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String tools = read(module.resolve("src/main/resources/static/javascript/reader-tools.js"));

        assertThat(tools)
            .contains("/user/reader-state/progress")
            .contains("/user/reader-state/annotations")
            .contains("absoluteTextOffset", "textPointAtOffset", "selectedAnchor")
            .contains("expectedVersion: item.version")
            .contains("readerState.authenticated")
            .doesNotContain("innerHTML");

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
            assertThat(read(reader)).as("Reader tool integration in %s", reader)
                .contains("/javascript/reader-tools.js?v=6")
                .doesNotContain("onselectstart=\"return false\"");
        }

        for (Path fragment : new Path[]{
            module.resolve("src/main/resources/templates/common/js.html"),
            module.resolve("src/main/resources/templates/mobile/common/js.html"),
            repository.resolve("templates/green/html/common/js.html"),
            repository.resolve("templates/green/html/mobile/common/js.html"),
            repository.resolve("templates/orange/html/common/js.html"),
            repository.resolve("templates/orange/html/mobile/common/js.html"),
            repository.resolve("templates/dark/html/common/js.html"),
            repository.resolve("templates/dark/html/mobile/common/js.html"),
            repository.resolve("templates/blue/html/common/js.html")
        }) {
            assertThat(read(fragment)).as("Reader state messages in %s", fragment)
                .contains("readerSyncSave", "readerBookmarkAdd", "readerNoteAdd", "readerAnnotationsShow");
        }
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
