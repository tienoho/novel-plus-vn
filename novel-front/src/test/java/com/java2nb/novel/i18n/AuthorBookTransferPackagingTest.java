package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorBookTransferPackagingTest {

    @Test
    void runtimeGreenAndOrangeExposeSameTransferUiWhileDarkBlueUseFallback() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        Path runtime = module.resolve("src/main/resources/templates/author/index_list.html");
        String expected = read(runtime);
        for (Path page : new Path[]{
            repository.resolve("templates/green/html/author/index_list.html"),
            repository.resolve("templates/orange/html/author/index_list.html")
        }) {
            assertThat(read(page)).as("UI transfer trong %s", page).isEqualTo(expected);
        }
        assertThat(expected)
            .contains("author.transfer.import", "author.transfer.export", "bookImportFile")
            .contains("/author/books/", "/import", "/export?format=")
            .contains("processData: false", "credentials: \"same-origin\"")
            .contains("file.size > 20 * 1024 * 1024", "flex-wrap: wrap");
        assertThat(repository.resolve("templates/dark/html/author/index_list.html")).doesNotExist();
        assertThat(repository.resolve("templates/blue/html/author/index_list.html")).doesNotExist();
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
