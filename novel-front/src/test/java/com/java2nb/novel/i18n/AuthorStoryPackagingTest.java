package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorStoryPackagingTest {

    @Test
    void runtimePageUsesPrivateApiAndTextNodes() throws Exception {
        String page = read(Path.of("src/main/resources/templates/author/story_bible.html"));
        int headerScript = page.indexOf("<script src=\"/javascript/header.js\"></script>");
        int commonScript = page.indexOf("<script src=\"/javascript/common.js?v=5\"></script>");

        assertThat(page)
            .contains("/author/story-items", "OUTLINE", "CHARACTER", "LOCATION", "TIMELINE")
            .contains(".addClass('story-content').text(item.content || '')")
            .contains("notice: /*[[#{common.notice}]]*/ 'Thông báo'", "title: storyMessages.notice")
            .contains(".header { min-width: 0; }")
            .contains(".header .box_center { width: auto; }")
            .contains(".story-shell.box_center { width: auto; }")
            .doesNotContain(".html(item.content");
        assertThat(headerScript).isGreaterThanOrEqualTo(0).isLessThan(commonScript);
    }

    @Test
    void authorThemeOverlaysKeepStoryLinkAndRuntimeFallback() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String marker = "/author/story_bible.html?bookId=";

        assertThat(read(module.resolve("src/main/resources/templates/author/index.html"))).contains(marker);
        assertThat(read(repository.resolve("templates/green/html/author/index.html"))).contains(marker);
        assertThat(read(repository.resolve("templates/orange/html/author/index.html"))).contains(marker);
        assertThat(repository.resolve("templates/dark/html/author/index.html")).doesNotExist();
        assertThat(repository.resolve("templates/blue/html/author/index.html")).doesNotExist();
    }

    @Test
    void migrationAndComposePackageAllFourTypes() throws Exception {
        Path repository = Path.of("").toAbsolutePath().normalize().getParent();
        String migration = read(repository.resolve("doc/sql/20260727_author_story_bible.sql"));
        String mapper = read(repository.resolve("novel-front/src/main/resources/mybatis/mapping/AuthorStoryMapper.xml"));
        String compose = read(repository.resolve("compose.yaml"));

        assertThat(migration)
            .contains("CREATE TABLE IF NOT EXISTS `author_story_item`")
            .contains("'OUTLINE', 'CHARACTER', 'LOCATION', 'TIMELINE'")
            .doesNotContain("DROP TABLE");
        assertThat(mapper).contains("version = version + 1", "version = #{expectedVersion}");
        assertThat(compose).contains("/migrations/20260727_author_story_bible.sql");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
