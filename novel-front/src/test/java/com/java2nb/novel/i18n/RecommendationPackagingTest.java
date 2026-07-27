package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationPackagingTest {

    @Test
    void mapperUsesPrivateBehaviorSignalsAndFailClosedContentFilters() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String mapper = read(module.resolve("src/main/resources/mybatis/mapping/BookMapper.xml"));
        assertThat(mapper).contains(
            "listRecommendations",
            "user_bookshelf",
            "user_read_history",
            "user_buy_record",
            "b.status = 1",
            "b.audit_status = 1",
            "b.cover_audit_status = 1",
            "coalesce(b.age_rating, 0) &lt;= #{maxAgeRating}",
            "known_shelf.user_id = #{userId}",
            "known_history.user_id = #{userId}",
            "known_purchase.user_id = #{userId}"
        );
        assertThat(mapper).doesNotContain("ORDER BY RAND()", "order by RAND()");
    }

    @Test
    void runtimeAndEveryExistingThemeShowPersonalizedHeading() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        List<Path> templates = List.of(
            module.resolve("src/main/resources/templates/index.html"),
            module.resolve("src/main/resources/templates/mobile/index.html"),
            repository.resolve("templates/green/html/index.html"),
            repository.resolve("templates/green/html/mobile/index.html"),
            repository.resolve("templates/orange/html/index.html"),
            repository.resolve("templates/orange/html/mobile/index.html"),
            repository.resolve("templates/dark/html/index.html"),
            repository.resolve("templates/dark/html/mobile/index.html"),
            repository.resolve("templates/blue/html/index.html")
        );
        for (Path template : templates) {
            assertThat(read(template))
                .as(template.toString())
                .contains("personalizedRecommendations", "#{home.forYou}", "#{home.featured}");
        }
        assertThat(repository.resolve("templates/blue/html/mobile/index.html")).doesNotExist();
    }

    @Test
    void idempotentIndexMigrationIsIncludedInComposeAndCacheIsVersioned() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String migration = read(repository.resolve("doc/sql/20260727_recommendation.sql"));
        assertThat(migration).contains(
            "idx_book_recommendation_pool",
            "idx_user_buy_recommendation",
            "information_schema.statistics"
        );
        assertThat(read(repository.resolve("compose.yaml")))
            .contains("/migrations/20260727_recommendation.sql");
        assertThat(read(repository.resolve(
            "novel-common/src/main/java/com/java2nb/novel/core/cache/CacheKey.java")))
            .contains("indexBookSettingsKey:v3");
    }

    @Test
    void everyPublicDiscoveryPathUsesModerationAndAgeFilters() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();
        String mapper = read(module.resolve("src/main/resources/mybatis/mapping/BookMapper.xml"));
        assertThat(mapper).contains(
            "AND status = 1",
            "AND audit_status = 1",
            "AND cover_audit_status = 1",
            "AND COALESCE(age_rating, 0) = 0"
        );

        String service = read(module.resolve(
            "src/main/java/com/java2nb/novel/service/impl/BookServiceImpl.java"));
        assertThat(service).contains(
            ".and(status, isEqualTo((byte) 1))",
            ".and(auditStatus, isEqualTo((byte) 1))",
            ".and(coverAuditStatus, isEqualTo((byte) 1))",
            ".and(ageRating, isEqualTo((byte) 0))",
            "BookIndexDynamicSqlSupport.auditStatus, isEqualTo((byte) 1)"
        );

        String cache = read(repository.resolve(
            "novel-common/src/main/java/com/java2nb/novel/core/cache/CacheKey.java"));
        assertThat(cache).contains(
            "indexClickBankBookKey:v2",
            "indexNewBookKey:v2",
            "indexUpdateBookKey:v2"
        );
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
