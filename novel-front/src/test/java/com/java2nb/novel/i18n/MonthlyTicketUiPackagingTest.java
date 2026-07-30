package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyTicketUiPackagingTest {

    @Test
    void sharedApiAndUiCoverAccountVoteSummaryAndRankingWithoutUnsafeHtml() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        String api = read(module.resolve("src/main/resources/static/javascript/gamification-api.js"));
        String ui = read(module.resolve("src/main/resources/static/javascript/monthly-ticket.js"));
        String css = read(module.resolve("src/main/resources/static/css/gamification.css"));
        String fragment = read(module.resolve(
            "src/main/resources/templates/common/monthly_ticket.html"));

        assertThat(api).contains(
            "getMonthlyTicketAccount", "getBookMonthlyTicketSummary",
            "castMonthlyTicketVote", "getMonthlyTicketRanking",
            "/user/monthly-tickets", "/monthly-ticket-summary",
            "/monthly-ticket-votes", "/book/monthly-ticket-ranking"
        );
        assertThat(ui)
            .contains("monthlyTicketBookApp", "monthlyTicketRankingApp", "monthlyTicketHomeApp",
                "initializeHomeWidget", "window.confirm", "window.setInterval",
                "pendingVote", "crypto.randomUUID", "expiringLots", "replaceChildren",
                "textContent")
            .doesNotContain("innerHTML", "insertAdjacentHTML", "document.write");
        assertThat(css)
            .contains(".monthly-ticket-widget", ".monthly-ticket-ranking", ".monthly-ticket-home",
                "@media (max-width: 640px)", "prefers-reduced-motion");
        assertThat(fragment)
            .contains("th:fragment=\"book_widget(bookId)\"", "id=\"monthlyTicketBookApp\"",
                "id=\"monthlyTicketExpiry\"",
                "th:fragment=\"ranking_widget\"", "id=\"monthlyTicketRankingApp\"",
                "th:fragment=\"home_widget\"", "id=\"monthlyTicketHomeApp\"",
                "aria-live=\"polite\"", "/javascript/monthly-ticket.js",
                "/javascript/gamification-api.js", "/css/gamification.css")
            .doesNotContain("innerHTML");
    }

    @Test
    void runtimeAndEveryExistingOverlayUseTheSharedFragment() throws Exception {
        Path module = Path.of("").toAbsolutePath().normalize();
        Path repository = module.getParent();

        for (Path detail : new Path[]{
            module.resolve("src/main/resources/templates/book/book_detail.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_detail.html"),
            repository.resolve("templates/green/html/book/book_detail.html"),
            repository.resolve("templates/green/html/mobile/book/book_detail.html"),
            repository.resolve("templates/orange/html/book/book_detail.html"),
            repository.resolve("templates/orange/html/mobile/book/book_detail.html"),
            repository.resolve("templates/dark/html/book/book_detail.html"),
            repository.resolve("templates/dark/html/mobile/book/book_detail.html")
        }) {
            assertThat(read(detail)).as("Widget Đuốc trong %s", detail)
                .contains("common/monthly_ticket :: book_widget(${book.id})");
        }

        for (Path home : new Path[]{
            module.resolve("src/main/resources/templates/index.html"),
            module.resolve("src/main/resources/templates/mobile/index.html"),
            repository.resolve("templates/green/html/index.html"),
            repository.resolve("templates/green/html/mobile/index.html"),
            repository.resolve("templates/orange/html/index.html"),
            repository.resolve("templates/orange/html/mobile/index.html"),
            repository.resolve("templates/dark/html/index.html"),
            repository.resolve("templates/dark/html/mobile/index.html"),
            repository.resolve("templates/blue/html/index.html")
        }) {
            assertThat(read(home)).as("Top Đuốc trong %s", home)
                .contains("common/monthly_ticket :: home_widget");
        }

        for (Path ranking : new Path[]{
            module.resolve("src/main/resources/templates/book/book_ranking.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_ranking.html"),
            repository.resolve("templates/green/html/book/book_ranking.html"),
            repository.resolve("templates/green/html/mobile/book/book_ranking.html"),
            repository.resolve("templates/orange/html/book/book_ranking.html"),
            repository.resolve("templates/orange/html/mobile/book/book_ranking.html"),
            repository.resolve("templates/dark/html/book/book_ranking.html"),
            repository.resolve("templates/dark/html/mobile/book/book_ranking.html")
        }) {
            assertThat(read(ranking)).as("Bảng Đuốc trong %s", ranking)
                .contains("common/monthly_ticket :: ranking_widget");
        }
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
