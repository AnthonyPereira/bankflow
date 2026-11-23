package com.bankflow.banking.api.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Parcours qui enchaînent plusieurs fonctionnalités : dépôt, virement, compensation, clôture. */
class CombinedPathsIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    private final String adminToken = createToken(UUID.randomUUID(), ADMIN);

    private record Holder(UUID userId, String token, String accountId, String iban) {}

    private Holder createHolder() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var account = parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));
        return new Holder(
                userId,
                token,
                account.get("id").asString(),
                account.get("accountNumber").asString());
    }

    private HttpResponse<String> operate(Holder holder, String kind, String amount)
            throws Exception {
        return send(
                "POST",
                "/accounts/" + holder.accountId() + "/" + kind,
                holder.token(),
                "{\"amount\":\"" + amount + "\"}");
    }

    private String deposit(Holder holder, String amount) throws Exception {
        var response = operate(holder, "deposits", amount);
        assertThat(response.statusCode()).isEqualTo(201);
        return parseJson(response).get("id").asString();
    }

    private HttpResponse<String> transfer(String token, Holder from, Holder to, String amount)
            throws Exception {
        return send(
                "POST",
                "/transfers",
                token,
                "{\"sourceAccountId\":\""
                        + from.accountId()
                        + "\",\"destinationIban\":\""
                        + to.iban()
                        + "\",\"amount\":\""
                        + amount
                        + "\"}",
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private HttpResponse<String> compensate(String transactionId) throws Exception {
        return send(
                "POST",
                "/transactions/" + transactionId + "/compensations",
                adminToken,
                "{\"reason\":\"Erreur de saisie\"}");
    }

    private String codeOf(HttpResponse<String> response) {
        return parseJson(response).get("code").asString();
    }

    @Test
    void compenserUnDepotDontLeSoldeAEteVireEstRefuse() throws Exception {
        var source = createHolder();
        var destination = createHolder();
        var depositId = deposit(source, "100.00");
        assertThat(transfer(source.token(), source, destination, "100.00").statusCode())
                .isEqualTo(201);

        var response = compensate(depositId);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("INSUFFICIENT_FUNDS");
        assertLedgerInvariants(jdbc, source.accountId(), destination.accountId());
    }

    @Test
    void compenserUnDepotEstPermisQuandLeSoldeRestantSuffit() throws Exception {
        var source = createHolder();
        var destination = createHolder();
        deposit(source, "100.00");
        var secondDepositId = deposit(source, "50.00");
        assertThat(transfer(source.token(), source, destination, "80.00").statusCode())
                .isEqualTo(201);

        var response = compensate(secondDepositId);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("balanceAfter").asString()).isEqualTo("20.00");
        assertLedgerInvariants(jdbc, source.accountId(), destination.accountId());
    }

    @Test
    void uneClotureEtUnVirementEntrantSimultanesRestentCoherents() throws Exception {
        for (int round = 0; round < 8; round++) {
            var source = createHolder();
            var destination = createHolder();
            deposit(source, "100.00");
            var start = new CountDownLatch(1);

            try (var executor = Executors.newFixedThreadPool(2)) {
                var transferCall =
                        executor.submit(() -> {
                            start.await();
                            return transfer(source.token(), source, destination, "10.00");
                        });
                var closeCall =
                        executor.submit(() -> {
                            start.await();
                            return send(
                                    "POST",
                                    "/accounts/" + destination.accountId() + "/close",
                                    destination.token(),
                                    null);
                        });
                start.countDown();
                var transferResponse = transferCall.get();
                var closeResponse = closeCall.get();

                if (transferResponse.statusCode() == 201) {
                    assertThat(closeResponse.statusCode()).isEqualTo(409);
                    assertThat(codeOf(closeResponse)).isEqualTo("ACCOUNT_BALANCE_NOT_ZERO");
                } else {
                    assertThat(transferResponse.statusCode()).isEqualTo(404);
                    assertThat(codeOf(transferResponse)).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
                    assertThat(closeResponse.statusCode()).isEqualTo(200);
                }
            }
            assertLedgerInvariants(jdbc, source.accountId(), destination.accountId());
        }
    }

    @Test
    void unClientSuspenduEstBloqueSurTousLesCheminsSaufLaClotureParUnConseiller() throws Exception {
        var holder = createHolder();
        var other = createHolder();
        deposit(holder, "100.00");
        assertThat(operate(holder, "withdrawals", "100.00").statusCode()).isEqualTo(201);
        jdbc.update(
                "UPDATE customer_refs SET status = 'SUSPENDED' WHERE user_id = ?", holder.userId());

        var responses =
                new ArrayList<HttpResponse<String>>(
                        List.of(
                                operate(holder, "deposits", "10.00"),
                                operate(holder, "withdrawals", "10.00"),
                                transfer(holder.token(), holder, other, "10.00"),
                                send("POST", "/accounts", holder.token(), "{\"currency\":\"EUR\"}"),
                                send(
                                        "POST",
                                        "/accounts/" + holder.accountId() + "/close",
                                        holder.token(),
                                        null),
                                send(
                                        "POST",
                                        "/accounts/" + holder.accountId() + "/deposits",
                                        adminToken,
                                        "{\"amount\":\"10.00\"}")));

        assertThat(responses)
                .allSatisfy(response -> {
                    assertThat(response.statusCode()).isEqualTo(403);
                    assertThat(codeOf(response)).isEqualTo("CUSTOMER_SUSPENDED");
                });
        var closedByAdmin =
                send("POST", "/accounts/" + holder.accountId() + "/close", adminToken, null);
        assertThat(closedByAdmin.statusCode()).isEqualTo(200);
        assertLedgerInvariants(jdbc, holder.accountId(), other.accountId());
    }
}
