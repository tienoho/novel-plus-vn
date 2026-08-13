package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseResidualSeedPackagingTest {
    private final Path repository = Path.of("..").toAbsolutePath().normalize();

    @Test
    void defaultFriendLinkIsLocalizedWithoutOverwritingCustomizedData() throws Exception {
        String migration = Files.readString(repository.resolve(
            "doc/sql/20260807_vi_friend_link.sql"), StandardCharsets.UTF_8);
        String flywayImage = Files.readString(repository.resolve("deploy/flyway/Dockerfile"),
            StandardCharsets.UTF_8);
        String cacheKeys = Files.readString(repository.resolve(
            "novel-common/src/main/java/com/java2nb/novel/core/cache/CacheKey.java"),
            StandardCharsets.UTF_8);

        assertThat(migration)
            .contains("SET `link_name` = 'Khởi Thư nguồn mở'")
            .contains("WHERE `id` = 5")
            .contains("AND `link_url` = 'https://novel.xxyopen.com'")
            .contains("AND `link_name` = '\u5c0f\u8bf4\u7cbe\u54c1\u5c4b'");
        assertThat(flywayImage).contains(
            "COPY --chmod=0444 doc/sql/20260807_vi_friend_link.sql "
                + "/flyway/sql/V2026080701__vi_friend_link.sql");
        assertThat(cacheKeys).contains("INDEX_LINK_KEY = \"indexLinkKey:vi-v2\"");
    }
}
