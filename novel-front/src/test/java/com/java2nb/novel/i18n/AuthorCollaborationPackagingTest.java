package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorCollaborationPackagingTest {

    @Test
    void runtimePageIsResponsiveUsesNaturalMessagesAndTextNodes() throws Exception {
        String page = read(Path.of("src/main/resources/templates/author/collaborators.html"));
        int headerScript = page.indexOf("<script src=\"/javascript/header.js\"></script>");
        int commonScript = page.indexOf("<script src=\"/javascript/common.js\"></script>");

        assertThat(page)
            .contains("/author/books/", "/collaborators", "/access")
            .contains("item.penName || item.username")
            .contains(".addClass('collab-tag').text(definition[1])")
            .contains("notice: /*[[#{common.notice}]]*/ 'Thông báo'")
            .contains(".header { min-width: 0; }")
            .contains(".header .box_center { width: auto; }")
            .contains(".collab-shell.box_center { width: auto; }")
            .doesNotContain(".html(item.");
        assertThat(headerScript).isGreaterThanOrEqualTo(0).isLessThan(commonScript);
    }

    @Test
    void allExistingAuthorIndexOverlaysUseAccessFlagsAndOwnerOnlyLinks() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        for (Path path : new Path[] {
            module.resolve("src/main/resources/templates/author/index.html"),
            repository.resolve("templates/green/html/author/index.html"),
            repository.resolve("templates/orange/html/author/index.html")
        }) {
            assertThat(read(path))
                .contains("book.canEditBook === true")
                .contains("book.canManageChapters === true || book.canPublishChapters === true")
                .contains("book.canManageStory === true")
                .contains("book.canViewAnalytics === true")
                .contains("book.ownerAccess === true")
                .contains("/author/collaborators.html?bookId=");
        }
        assertThat(repository.resolve("templates/dark/html/author/index.html")).doesNotExist();
        assertThat(repository.resolve("templates/blue/html/author/index.html")).doesNotExist();
    }

    @Test
    void migrationComposeAndMapperPackageOptimisticImmutableAuthorization() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String migration = read(repository.resolve("doc/sql/20260727_author_collaboration.sql"));
        String mapper = read(repository.resolve(
            "novel-front/src/main/resources/mybatis/mapping/AuthorBookCollaborationMapper.xml"));
        String compose = read(repository.resolve("compose.yaml"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `author_book_collaborator`")
            .contains("CREATE TABLE IF NOT EXISTS `author_book_collaborator_audit`")
            .contains("author_book_collaborator_audit is immutable")
            .doesNotContain("DROP TABLE");
        assertThat(mapper)
            .contains("version = version + 1")
            .contains("version = #{expectedVersion}")
            .contains("b.author_id = #{actorAuthorId} OR c.id IS NOT NULL");
        assertThat(compose).contains("/migrations/20260727_author_collaboration.sql");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
