package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/** Outils du conseiller : ouverture au nom d'un client, filtre par client et vue d'ensemble. */
class AccountAdminApiIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    private final String adminToken = createToken(UUID.randomUUID(), ADMIN);

    @Test
    void unAdminSansCustomerIdRecoit400() throws Exception {
        var response = openAccount(adminToken, "{\"currency\":\"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        var body = parseJson(response);
        assertThat(body.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("errors").get("customerId").asString())
                .isEqualTo("Choisissez le client.");
    }

    @Test
    void unAdminOuvreUnCompteAuNomDUnClientActif() throws Exception {
        var customer = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);

        var response = openAccountFor(adminToken, customer.getCustomerId(), "USD");

        assertThat(response.statusCode()).isEqualTo(201);
        var account = parseJson(response);
        assertThat(account.get("customerId").asString())
                .isEqualTo(customer.getCustomerId().toString());
        assertThat(account.get("currency").asString()).isEqualTo("USD");
        assertThat(account.get("balance").asString()).isEqualTo("0.00");
        var clientToken = createToken(customer.getUserId(), CLIENT);
        var listed = parseJson(send("GET", "/accounts", clientToken, null));
        assertThat(listed.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void unClientInconnuDonne404CustomerNotFound() throws Exception {
        var response = openAccountFor(adminToken, UUID.randomUUID(), "EUR");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    void unClientSuspenduDonne403PourLAdmin() throws Exception {
        var customer = createCustomerRef(UUID.randomUUID(), CustomerStatus.SUSPENDED);

        var response = openAccountFor(adminToken, customer.getCustomerId(), "EUR");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("CUSTOMER_SUSPENDED");
    }

    @Test
    void laLimiteDeCinqComptesDuClientEstRespecteePourLAdmin() throws Exception {
        var customer = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        for (int i = 0; i < 5; i++) {
            assertThat(openAccountFor(adminToken, customer.getCustomerId(), "EUR").statusCode())
                    .isEqualTo(201);
        }

        var response = openAccountFor(adminToken, customer.getCustomerId(), "EUR");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("ACCOUNT_LIMIT_REACHED");
    }

    @Test
    void unClientQuiEnvoieUnCustomerIdRecoit400() throws Exception {
        var customer = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        var other = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);

        var response =
                openAccountFor(
                        createToken(customer.getUserId(), CLIENT), other.getCustomerId(), "EUR");

        assertThat(response.statusCode()).isEqualTo(400);
        var body = parseJson(response);
        assertThat(body.get("code").asString()).isEqualTo("VALIDATION_ERROR");
        assertThat(body.get("errors").get("customerId").asString())
                .isEqualTo("Ce champ est réservé aux conseillers.");
        var otherAccounts =
                send("GET", "/accounts?customerId=" + other.getCustomerId(), adminToken, null);
        assertThat(parseJson(otherAccounts).get("totalElements").asInt()).isZero();
    }

    @Test
    void leFiltreParClientNeVautQuePourLAdmin() throws Exception {
        var first = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        var second = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        openAccountFor(adminToken, first.getCustomerId(), "EUR");
        openAccountFor(adminToken, first.getCustomerId(), "USD");
        openAccountFor(adminToken, second.getCustomerId(), "EUR");

        var filtered =
                parseJson(
                        send(
                                "GET",
                                "/accounts?customerId=" + first.getCustomerId(),
                                adminToken,
                                null));
        assertThat(filtered.get("totalElements").asInt()).isEqualTo(2);
        assertThat(
                        filtered.get("content")
                                .valueStream()
                                .map(node -> node.get("customerId").asString()))
                .containsOnly(first.getCustomerId().toString());

        var ignored =
                parseJson(
                        send(
                                "GET",
                                "/accounts?customerId=" + first.getCustomerId(),
                                createToken(second.getUserId(), CLIENT),
                                null));
        assertThat(ignored.get("totalElements").asInt()).isEqualTo(1);
        assertThat(ignored.get("content").get(0).get("customerId").asString())
                .isEqualTo(second.getCustomerId().toString());
    }

    @Test
    void leResumeDonneLesTotauxDeLaBanque() throws Exception {
        var before = parseJson(send("GET", "/accounts/summary", adminToken, null));
        var active = createCustomerRef(UUID.randomUUID(), CustomerStatus.ACTIVE);
        createCustomerRef(UUID.randomUUID(), CustomerStatus.SUSPENDED);
        var eurOne = openAccountFor(adminToken, active.getCustomerId(), "EUR");
        var eurTwo = openAccountFor(adminToken, active.getCustomerId(), "EUR");
        var usd = openAccountFor(adminToken, active.getCustomerId(), "USD");
        var toClose = openAccountFor(adminToken, active.getCustomerId(), "EUR");
        setBalance(eurOne, "100.50");
        setBalance(eurTwo, "20.25");
        setBalance(usd, "7.00");
        send(
                "POST",
                "/accounts/" + parseJson(toClose).get("id").asString() + "/close",
                adminToken,
                null);

        var response = send("GET", "/accounts/summary", adminToken, null);

        assertThat(response.statusCode()).isEqualTo(200);
        var after = parseJson(response);
        assertThat(delta(before, after, "customers", "total")).isEqualTo(2);
        assertThat(delta(before, after, "customers", "active")).isEqualTo(1);
        assertThat(delta(before, after, "customers", "suspended")).isEqualTo(1);
        assertThat(delta(before, after, "accounts", "open")).isEqualTo(3);
        assertThat(delta(before, after, "accounts", "closed")).isEqualTo(1);
        assertThat(totalBalance(after, "EUR").subtract(totalBalance(before, "EUR")))
                .isEqualByComparingTo("120.75");
        assertThat(totalBalance(after, "USD").subtract(totalBalance(before, "USD")))
                .isEqualByComparingTo("7.00");
        var currencies =
                after.get("totalsByCurrency")
                        .valueStream()
                        .map(node -> node.get("currency").asString())
                        .toList();
        assertThat(currencies).isSorted();
    }

    @Test
    void leResumeEstInterditAuxClients() throws Exception {
        var token = createToken(UUID.randomUUID(), CLIENT);

        var response = send("GET", "/accounts/summary", token, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void leResumeNEstPasCaptureParLeCheminAvecIdentifiant() throws Exception {
        assertThat(send("GET", "/accounts/summary", adminToken, null).statusCode()).isEqualTo(200);

        var invalid = send("GET", "/accounts/pas-un-uuid", adminToken, null);

        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(parseJson(invalid).get("code").asString()).isEqualTo("VALIDATION_ERROR");
    }

    private HttpResponse<String> openAccount(String token, String body) throws Exception {
        return send("POST", "/accounts", token, body);
    }

    private HttpResponse<String> openAccountFor(String token, UUID customerId, String currency)
            throws Exception {
        return openAccount(
                token, "{\"currency\":\"" + currency + "\",\"customerId\":\"" + customerId + "\"}");
    }

    private void setBalance(HttpResponse<String> account, String balance) {
        jdbc.update(
                "UPDATE accounts SET balance = ?::numeric WHERE id = ?::uuid",
                balance,
                parseJson(account).get("id").asString());
    }

    private static long delta(JsonNode before, JsonNode after, String group, String field) {
        return after.get(group).get(field).asLong() - before.get(group).get(field).asLong();
    }

    private static BigDecimal totalBalance(JsonNode summary, String currency) {
        return summary.get("totalsByCurrency")
                .valueStream()
                .filter(node -> currency.equals(node.get("currency").asString()))
                .map(node -> new BigDecimal(node.get("balance").asString()))
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }
}
