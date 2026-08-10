package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReadingTicketUiPackagingTest {
    private final Path module = Path.of("").toAbsolutePath().normalize();
    private final Path repository = module.getParent();

    @Test
    void accountPageUsesExistingSessionOwnedApisAndRuntimeFallback() throws Exception {
        String controller = read(module.resolve(
            "src/main/java/com/java2nb/novel/controller/page/PageController.java"));
        String desktop = read(module.resolve(
            "src/main/resources/templates/user/reading_tickets.html"));
        String mobile = read(module.resolve(
            "src/main/resources/templates/mobile/user/reading_tickets.html"));
        String script = read(module.resolve(
            "src/main/resources/static/javascript/reading-ticket.js"));
        String css = read(module.resolve("src/main/resources/static/css/reading-ticket.css"));

        assertThat(controller).contains(
            "@RequestMapping(\"user/reading_tickets.html\")",
            "getUserDetails(request) == null",
            "ThreadLocalUtil.getTemplateDir() + \"user/reading_tickets\"");
        for (String page : new String[] {desktop, mobile}) {
            assertThat(page).contains(
                "id=\"readingTicketAccountApp\"",
                "data-account-endpoint=\"/user/reading-tickets\"",
                "data-ledger-endpoint=\"/user/reading-tickets/ledger\"",
                "data-lots-endpoint=\"/user/reading-tickets/lots\"",
                "data-plans-endpoint=\"/user/reading-subscriptions/plans\"",
                "data-current-endpoint=\"/user/reading-subscriptions/current\"",
                "data-checkout-endpoint=\"/user/reading-subscriptions/checkouts\"",
                "data-channels-endpoint=\"/pay/channels\"",
                "data-grants-endpoint=\"/user/reading-subscriptions/{id}/period-grants\"",
                "data-mandate-endpoint=\"/user/reading-subscriptions/mandates/vnpay\"",
                "data-mandate-state-endpoint=\"/user/reading-subscriptions/mandates/vnpay/current\"",
                "data-renewal-settings-endpoint=\"/user/reading-subscriptions/{id}/renewal-settings\"",
                "data-price-consent-endpoint=\"/user/reading-subscriptions/{id}/price-consents\"",
                "data-cancel-endpoint=\"/user/reading-subscriptions/{id}/cancel\"",
                "data-price-unavailable",
                "id=\"readingTicketLedgerHistory\"",
                "id=\"readingTicketLotHistory\"",
                "/javascript/reading-ticket.js", "/css/reading-ticket.css");
        }
        assertThat(script)
            .contains("credentials: 'same-origin'", "replaceChildren()", "textContent",
                "pendingRequestId", "if (!pendingRequestId)", "root.dataset.unlockUncertain",
                "plan.priceVnd", "VND", "payload.clientRequestId = requestId('checkout')",
                "clientRequestId: requestId('mandate')", "clientRequestId: requestId('consent')",
                "headers['X-XSRF-TOKEN']", "form.method = 'POST'",
                "jsonOptions('POST', {clientRequestId: pendingRequestId})",
                "['ispTxnId', data.ispTxnId]", "['tmnCode', data.tmnCode]",
                "['dataKey', data.dataKey]", "PAUSED: root.dataset.statusPaused",
                "root.dataset.checkoutUncertain", "loadLedgerHistory", "loadLotHistory",
                "root.dataset.ledgerEndpoint", "root.dataset.lotsEndpoint")
            .doesNotContain("innerHTML");
        assertThat(css).contains(
            "@media (max-width: 640px)", "@media (prefers-reduced-motion: reduce)",
            ".reading-ticket-status.is-error", ".reading-ticket-renewal-controls",
            ".reading-ticket-mandate", ".reading-ticket-current-actions",
            ".reading-ticket-current-actions .reading-ticket-danger",
            ".reading-ticket-success", ".reading-ticket-warning",
            ".reading-ticket-renewal-controls select:focus-visible");
    }

    @Test
    void paidChapterOffersTicketUnlockAcrossRuntimeAndThemeOverrides() throws Exception {
        String fragment = read(module.resolve(
            "src/main/resources/templates/common/reading_ticket_unlock.html"));
        assertThat(fragment).contains(
            "id=\"readingTicketUnlock\"",
            "data-account-endpoint=\"/user/reading-tickets\"",
            "reading-ticket-unlock'",
            "data-reading-ticket-action",
            "aria-live=\"polite\"");

        for (Path page : new Path[] {
            module.resolve("src/main/resources/templates/book/book_content.html"),
            module.resolve("src/main/resources/templates/mobile/book/book_content.html"),
            repository.resolve("templates/green/html/book/book_content.html"),
            repository.resolve("templates/green/html/mobile/book/book_content.html"),
            repository.resolve("templates/orange/html/book/book_content.html"),
            repository.resolve("templates/orange/html/mobile/book/book_content.html"),
            repository.resolve("templates/dark/html/book/book_content.html"),
            repository.resolve("templates/dark/html/mobile/book/book_content.html")
        }) {
            assertThat(read(page)).contains(
                "th:if=\"${needBuy}\"",
                "common/reading_ticket_unlock :: unlock",
                "/javascript/reading-ticket.js?v=2",
                "/css/reading-ticket.css");
        }
    }

    @Test
    void userCenterLinksToTicketAccountWhereThemesOverrideIt() throws Exception {
        for (Path page : new Path[] {
            module.resolve("src/main/resources/templates/user/userinfo.html"),
            module.resolve("src/main/resources/templates/mobile/user/userinfo.html"),
            repository.resolve("templates/green/html/user/userinfo.html"),
            repository.resolve("templates/orange/html/user/userinfo.html")
        }) {
            assertThat(read(page)).contains("/user/reading_tickets.html");
        }
    }

    @Test
    void ticketUiMessagesHaveVietnameseAndChineseParity() throws Exception {
        Set<String> vietnamese = keys(module.resolve(
            "src/main/resources/i18n/messages_vi_VN.properties"));
        Set<String> chinese = keys(module.resolve(
            "src/main/resources/i18n/messages_zh_CN.properties"));

        assertThat(vietnamese).isNotEmpty().containsExactlyInAnyOrderElementsOf(chinese);
    }

    private Set<String> keys(Path properties) throws Exception {
        Set<String> keys = new LinkedHashSet<>();
        for (String line : Files.readAllLines(properties, StandardCharsets.UTF_8)) {
            if (line.startsWith("reader.ticket.")) {
                keys.add(line.substring(0, line.indexOf('=')));
            }
        }
        return keys;
    }

    private String read(Path path) throws Exception {
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
