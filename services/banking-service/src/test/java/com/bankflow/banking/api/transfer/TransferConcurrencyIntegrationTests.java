package com.bankflow.banking.api.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Les virements simultanés sont sérialisés par les verrous de comptes pris dans un ordre fixe. */
class TransferConcurrencyIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    private record Holder(String token, String accountId, String iban) {}

    private Holder createHolder(String deposit) throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var account = parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));
        var accountId = account.get("id").asString();
        send(
                "POST",
                "/accounts/" + accountId + "/deposits",
                token,
                "{\"amount\":\"" + deposit + "\"}");
        return new Holder(token, accountId, account.get("accountNumber").asString());
    }

    private HttpResponse<String> transfer(Holder from, Holder to, String amount) throws Exception {
        return send(
                "POST",
                "/transfers",
                from.token(),
                "{\"sourceAccountId\":\""
                        + from.accountId()
                        + "\",\"destinationIban\":\""
                        + to.iban()
                        + "\",\"amount\":\""
                        + amount
                        + "\"}",
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private BigDecimal balanceOf(String accountId) {
        return jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
    }

    @Test
    void desVirementsCroisesSimultanesNeSInterbloquentPasEtConserventLaSomme() throws Exception {
        var a = createHolder("1000.00");
        var b = createHolder("1000.00");
        var calls = new ArrayList<Callable<HttpResponse<String>>>();
        for (int i = 0; i < 25; i++) {
            calls.add(() -> transfer(a, b, "10.00"));
            calls.add(() -> transfer(b, a, "10.00"));
        }

        var responses = runConcurrently(calls);

        assertThat(responses.stream().map(HttpResponse::statusCode)).containsOnly(201);
        assertThat(balanceOf(a.accountId())).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(b.accountId())).isEqualByComparingTo("1000.00");
        assertThat(balanceOf(a.accountId()).add(balanceOf(b.accountId())))
                .isEqualByComparingTo("2000.00");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM transfers WHERE source_account_id IN"
                                        + " (?::uuid, ?::uuid)",
                                Long.class,
                                a.accountId(),
                                b.accountId()))
                .isEqualTo(50L);
    }

    @Test
    void trenteVirementsSimultanesDeDixDepuisUnCompteDeCentEnAcceptentExactementDix()
            throws Exception {
        var source = createHolder("100.00");
        var destination = createHolder("0.01");
        var calls = new ArrayList<Callable<HttpResponse<String>>>();
        for (int i = 0; i < 30; i++) {
            calls.add(() -> transfer(source, destination, "10.00"));
        }

        var responses = runConcurrently(calls);

        assertThat(responses.stream().filter(r -> r.statusCode() == 201)).hasSize(10);
        var refused = responses.stream().filter(r -> r.statusCode() == 409).toList();
        assertThat(refused).hasSize(20);
        assertThat(refused)
                .allSatisfy(r ->
                        assertThat(parseJson(r).get("code").asString())
                                .isEqualTo("INSUFFICIENT_FUNDS"));
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("0");
        assertThat(balanceOf(destination.accountId())).isEqualByComparingTo("100.01");
    }

    private static List<HttpResponse<String>> runConcurrently(
            List<Callable<HttpResponse<String>>> calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.size())) {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (var call : calls) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            return call.call();
                        }));
            }
            start.countDown();
            var responses = new ArrayList<HttpResponse<String>>();
            for (var future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }
}
