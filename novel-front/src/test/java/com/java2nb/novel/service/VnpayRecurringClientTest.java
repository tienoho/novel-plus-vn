package com.java2nb.novel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayRecurringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VnpayRecurringClientTest {

    private VnpayRecurringProperties properties;
    private VnpayRecurringSigner signer;
    private VnpayRecurringClient client;
    private VnpayRecurringMandateCommand command;

    @BeforeEach
    void setUp() {
        properties = new VnpayRecurringProperties();
        properties.setEnabled(true);
        properties.setClientId("CLIENT01");
        properties.setUsername("merchantuser");
        properties.setPassword("a-secure-password");
        properties.setClientSecret("a-secure-client-secret");
        properties.setTmnCode("VNPAYREC");
        properties.setHashSecret("0123456789abcdef0123456789abcdef");
        properties.setReturnUrl("https://example.com/return");
        properties.setCancelUrl("https://example.com/cancel");
        signer = new VnpayRecurringSigner(properties);
        client = new VnpayRecurringClient(properties, signer, new ObjectMapper(), HttpClient.newHttpClient());
        command = new VnpayRecurringMandateCommand("17000000000000001", "NP123", 123L,
            100_000L, 1, LocalDate.of(2026, 9, 1), LocalDateTime.of(2026, 8, 1, 12, 0),
            "127.0.0.1", "NovelPlusWeb");
    }

    @Test
    void buildsDocumentedMandatePayload() {
        Map<String, Object> payload = client.buildMandatePayload(command);

        assertThat(payload).containsEntry("command", "recurring").containsEntry("tmnCode", "VNPAYREC");
        Map<?, ?> transaction = (Map<?, ?>) payload.get("transaction");
        assertThat(transaction.get("recurringAmount")).isEqualTo(10_000_000L);
        assertThat(transaction.get("recurringFrequency")).isEqualTo("month");
        assertThat(transaction.get("recurringNumber")).isEqualTo(0);
        assertThat(transaction.get("amount")).isEqualTo(0);
        assertThat(payload.get("secureHash")).isEqualTo(
            "0f9e1ff5574c1b756da5c9917f42d1acfacb945327f2a17f55f234afac03bee8"
                + "e1d452a46b6802ad825d12c8c1faf8b9b647698b394445bb60dbe62f0915ccee");
    }

    @Test
    void acceptsOnlySignedProviderResponse() throws Exception {
        String hash = signer.signFields(List.of("00", "Successful", "666821925535879168",
            0, 0, "VND", "", "signed-data-key"));
        String response = """
            {"rspCode":"00","rspMsg":"Successful","addData":"","transaction":{
            "id":"666821925535879168","amount":0,"feeAmount":0,"currCode":"VND"},
            "dataKey":"signed-data-key","secureHash":"%s"}
            """.formatted(hash);

        VnpayRecurringMandateInitialization result = client.parseMandateResponse(command, response);
        assertThat(result.providerRecurringId()).isEqualTo("666821925535879168");
        assertThat(result.dataKey()).isEqualTo("signed-data-key");

        assertThatThrownBy(() -> client.parseMandateResponse(command,
            response.replace("signed-data-key", "tampered-data-key")))
            .isInstanceOf(VnpayRecurringUnavailableException.class);
    }

    @Test
    void buildsChargePayloadWithDocumentedChecksum() {
        VnpayRecurringChargeCommand charge = chargeCommand();

        Map<String, Object> payload = client.buildChargePayload(charge);

        assertThat(payload.get("command")).isEqualTo("recurring_pay");
        assertThat(payload.get("secureHash")).isEqualTo(
            "07f8bd33c8662b7a0a0304b04e77e15f01c1eb33c50aa3298ea82044f5f91678"
                + "3759971b37fc5495f5d6400d2bcbd36d04bab149373e9f38b9fe0c91dfcecb37");
    }

    @Test
    void settlesOnlySignedMatchingChargeResponse() throws Exception {
        VnpayRecurringChargeCommand charge = chargeCommand();
        String hash = signer.signFields(List.of("00", "Successful", "777821925535879168",
            4_900_000L, 0, "VND", ""));
        String response = """
            {"rspCode":"00","rspMsg":"Successful","addData":"","transaction":{
            "id":"777821925535879168","amount":4900000,"feeAmount":0,"currCode":"VND"},
            "secureHash":"%s"}
            """.formatted(hash);

        assertThat(client.parseChargeResponse(charge, response).status())
            .isEqualTo(VnpayRecurringChargeResult.Status.SETTLED);
        assertThat(client.parseChargeResponse(charge, response.replace("4900000", "4800000")).status())
            .isEqualTo(VnpayRecurringChargeResult.Status.PENDING);
    }

    @Test
    void ambiguousSignedProviderCodeStaysPending() throws Exception {
        String hash = signer.signFields(List.of("99", "System error", "", 0, 0, "", ""));
        String response = """
            {"rspCode":"99","rspMsg":"System error","addData":"","secureHash":"%s"}
            """.formatted(hash);

        assertThat(client.parseChargeResponse(chargeCommand(), response).status())
            .isEqualTo(VnpayRecurringChargeResult.Status.PENDING);
    }

    private VnpayRecurringChargeCommand chargeCommand() {
        return new VnpayRecurringChargeCommand("17000000000000002", "NPR81A1",
            "666821925535879168", "tokenABC123", 49_000L, LocalDate.of(2027, 2, 1),
            LocalDateTime.of(2027, 2, 1, 7, 0));
    }
}
