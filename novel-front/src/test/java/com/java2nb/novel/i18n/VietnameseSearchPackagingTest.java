package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class VietnameseSearchPackagingTest {

    @Test
    void migrationAndRuntimeQueryArePackagedTogether() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();

        String migration = read(repository.resolve("doc/sql/20260726_vietnamese_search.sql"));
        assertThat(migration)
            .contains("book_name_search", "author_name_search", "ft_book_vi_search", "WITH PARSER ngram");

        String flywayImage = read(repository.resolve("deploy/flyway/Dockerfile"));
        assertThat(flywayImage).contains(
            "COPY --chmod=0444 doc/sql/20260726_vietnamese_search.sql "
                + "/flyway/sql/V2026072605__vietnamese_search.sql");

        String mapper = read(module.resolve("src/main/resources/mybatis/mapping/BookMapper.xml"));
        assertThat(mapper)
            .contains("searchFuzzyCandidates", "MATCH(book_name_search, author_name_search)", "LIMIT #{limit}");

        String matcher = read(module.resolve(
            "src/main/java/com/java2nb/novel/service/search/VietnameseSearchMatcher.java"));
        assertThat(matcher).contains("Normalizer.Form.NFD", "damerauLevenshtein", "maximumDistance");
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
