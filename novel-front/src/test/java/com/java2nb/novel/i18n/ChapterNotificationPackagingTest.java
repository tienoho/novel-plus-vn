package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ChapterNotificationPackagingTest {

    @Test
    void migrationAndComposeOutboxStayTogether() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String migration = read(repository.resolve("doc/sql/20260726_chapter_notifications.sql"));
        assertThat(migration).contains(
            "chapter_publish_event", "user_notification", "user_author_follow",
            "trg_book_index_publish_event_insert", "trg_book_index_publish_event_approve"
        );
        assertThat(read(repository.resolve("deploy/flyway/Dockerfile")))
            .contains("COPY --chmod=0444 doc/sql/20260726_chapter_notifications.sql "
                + "/flyway/sql/V2026072606__chapter_notifications.sql");
    }

    @Test
    void inboxBadgeAndFollowControlsArePackagedForDesktopAndMobileThemes() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        assertThat(read(module.resolve("src/main/resources/templates/user/notifications.html")))
            .contains("/user/notifications", "notifications/read-all", "notificationList");
        assertThat(read(module.resolve("src/main/resources/templates/mobile/user/notifications.html")))
            .contains("/user/notifications", "notifications/read-all", "notificationList");
        assertThat(read(module.resolve("src/main/resources/static/javascript/notification-badge.js")))
            .contains("/user/notifications/unread-count", "data-notification-badge");
        assertThat(read(module.resolve("src/main/resources/static/javascript/author-follow.js")))
            .contains("/user/follows/authors/", "data-author-follow");

        for (Path detail : new Path[]{
            module.resolve("src/main/resources/templates/book/book_detail.html"),
            repository.resolve("templates/green/html/book/book_detail.html"),
            repository.resolve("templates/orange/html/book/book_detail.html"),
            repository.resolve("templates/dark/html/book/book_detail.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_detail.html"),
            repository.resolve("templates/green/html/mobile/book/book_detail.html"),
            repository.resolve("templates/orange/html/mobile/book/book_detail.html"),
            repository.resolve("templates/dark/html/mobile/book/book_detail.html")
        }) {
            assertThat(read(detail)).contains("data-author-follow", "/javascript/author-follow.js");
        }

        for (Path navigation : new Path[]{
            module.resolve("src/main/resources/templates/common/top.html"),
            repository.resolve("templates/green/html/common/top.html"),
            repository.resolve("templates/orange/html/common/top.html"),
            repository.resolve("templates/blue/html/common/top.html"),
            module.resolve("src/main/resources/templates/mobile/common/footer.html"),
            repository.resolve("templates/green/html/mobile/common/footer.html"),
            repository.resolve("templates/orange/html/mobile/common/footer.html"),
            repository.resolve("templates/dark/html/mobile/common/footer.html")
        }) {
            assertThat(read(navigation)).contains("data-notification-badge", "/user/notifications.html");
        }
    }

    @Test
    void dockerImageMergesRuntimeBaseBeforeThemeOverrides() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String dockerfile = read(repository.resolve("deploy/docker/Dockerfile"));

        assertThat(dockerfile)
            .contains("/workspace/novel-front/src/main/resources/templates/.")
            .contains("/workspace/novel-front/src/main/resources/static/.")
            .contains("/workspace/templates/$theme/html/.")
            .contains("/workspace/templates/$theme/static/.")
            .contains("/workspace/packaged-themes /app/templates");
        assertThat(dockerfile.indexOf("src/main/resources/templates/."))
            .isLessThan(dockerfile.indexOf("templates/$theme/html/."));
        assertThat(dockerfile.indexOf("src/main/resources/static/."))
            .isLessThan(dockerfile.indexOf("templates/$theme/static/."));
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
