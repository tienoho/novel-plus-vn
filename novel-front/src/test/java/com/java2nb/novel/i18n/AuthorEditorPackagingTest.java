package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorEditorPackagingTest {

    @Test
    void themeOverlaysKeepTheEditorEnabled() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();

        for (String page : new String[]{"content_add.html", "content_update.html"}) {
            String runtime = read(module.resolve("src/main/resources/templates/author").resolve(page));
            assertThat(read(repository.resolve("templates/green/html/author").resolve(page))).isEqualTo(runtime);
            assertThat(read(repository.resolve("templates/orange/html/author").resolve(page))).isEqualTo(runtime);
            assertThat(runtime).contains("/javascript/author-editor.js", "btnSaveDraft", "btnScheduleDraft")
                .doesNotContain("onclick=\"addBookContent()\"");
        }
    }

    @Test
    void scheduledDraftPublishingDoesNotAttemptAutosave() throws Exception {
        Path script = Path.of("src/main/resources/static/javascript/author-editor.js")
            .toAbsolutePath().normalize();
        String source = read(script);

        assertThat(source)
            .contains("var beforePublish = self.state.status === 'SCHEDULED'")
            .contains("this.updateControls();")
            .contains("mã bản nháp vẫn được giữ để thử lại");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
