package com.java2nb.novel.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorPayoutFourEyesPackagingTest {

    @Test
    void mapperAndUiEnforceSeparatedPayoutPhases() throws Exception {
        String mapper = read("src/main/resources/mybatis/novel/AuthorFinanceReviewMapper.xml");
        String template = read("src/main/resources/templates/novel/authorFinance/authorFinance.html");

        assertThat(mapper)
            .contains("approved_by")
            .contains("executed_by")
            .contains("approved_by &lt;&gt; #{executorId}")
            .contains("executed_by = #{executorId}");
        assertThat(template)
            .contains("novel:authorFinance:payout:approve")
            .contains("novel:authorFinance:payout:execute")
            .doesNotContain("novel:authorFinance:payout\"");
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
