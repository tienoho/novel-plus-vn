package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorAiPackagingTest {

    @Test
    void draftTextUsesAuthenticatedPostBodyAndNoLegacyEventSource() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String controller = read(module.resolve(
            "src/main/java/com/java2nb/novel/controller/AuthorController.java"));
        String client = read(module.resolve("src/main/resources/static/javascript/author-ai.js"));

        assertThat(controller)
            .contains("@PostMapping(\"ai/expand\")", "@PostMapping(\"ai/condense\")",
                "@PostMapping(\"ai/continue\")", "@PostMapping(\"ai/polish\")",
                "@RequestBody AuthorAiRequest", "checkAuthor(request)")
            .doesNotContain("ai/stream/", "MediaType.TEXT_EVENT_STREAM_VALUE", "Flux<String>");
        assertThat(client)
            .contains("type: 'POST'", "contentType: 'application/json; charset=UTF-8'",
                "data: JSON.stringify(payload)", "bookId: Number(config.bookId)")
            .doesNotContain("EventSource", "?text=", "console.log", "console.error");
    }

    @Test
    void runtimeAndThemeEditorsUseSharedProtectedClient() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        for (String name : new String[]{"content_add.html", "content_update.html"}) {
            String runtime = read(module.resolve("src/main/resources/templates/author/" + name));
            assertThat(runtime)
                .contains("/javascript/author-ai.js?v=1", "data-operation=\"expand\"",
                    "ai.provenance.notice", "role=\"toolbar\"")
                .doesNotContain("EventSource", "data-type=\"stream/", "?text=");
            assertThat(read(repository.resolve("templates/green/html/author/" + name))).isEqualTo(runtime);
            assertThat(read(repository.resolve("templates/orange/html/author/" + name))).isEqualTo(runtime);
        }
    }

    @Test
    void provenanceSchemaIsImmutableAndStoresNoRawDraftText() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String migration = read(repository.resolve("doc/sql/20260728_author_ai.sql"));
        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `author_ai_usage`", "`input_sha256`", "`output_sha256`",
                "trg_author_ai_usage_no_update", "trg_author_ai_usage_no_delete", "SIGNAL SQLSTATE '45000'")
            .doesNotContain("source_text", "output_text", "story_context", "mediumtext", "longtext");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
