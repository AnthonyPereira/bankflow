package com.bankflow.banking.api.operation;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Les opérations simultanées sont sérialisées par le verrou du compte. */
class OperationConcurrencyIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void cinquanteRetraitsSimultanesDeDixSurUnSoldeDeCentEnAcceptentExactementDix()
            throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();
        send("POST", "/accounts/" + accountId + "/deposits", token, "{\"amount\":\"100.00\"}");

        var statuses = runConcurrently(50, () -> withdraw(token, accountId, null));

        assertThat(statuses.stream().filter(status -> status == 201)).hasSize(10);
        assertThat(statuses.stream().filter(status -> status == 409)).hasSize(40);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void dixRequetesSimultanneesAvecLaMemeCleNeFontQuUneOperation() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();
        send("POST", "/accounts/" + accountId + "/deposits", token, "{\"amount\":\"100.00\"}");

        var statuses = runConcurrently(10, () -> withdraw(token, accountId, "meme-cle"));

        assertThat(statuses).containsOnly(201);
        assertThat(balanceOf(accountId)).isEqualByComparingTo("90.00");
    }

    @Test
    void dixCompensationsSimultaneesDeLaMemeOperationEnAcceptentUneSeule() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();
        send("POST", "/accounts/" + accountId + "/deposits", token, "{\"amount\":\"100.00\"}");
        var depositId =
                parseJson(
                                send(
                                        "POST",
                                        "/accounts/" + accountId + "/deposits",
                                        token,
                                        "{\"amount\":\"40.00\"}"))
                        .get("id")
                        .asString();

        var responses =
                runRequests(
                        10,
                        () ->
                                send(
                                        "POST",
                                        "/transactions/" + depositId + "/compensations",
                                        adminToken,
                                        "{\"reason\":\"Erreur de saisie\"}"));

        assertThat(responses.stream().map(HttpResponse::statusCode).filter(s -> s == 201))
                .hasSize(1);
        var conflicts = responses.stream().filter(r -> r.statusCode() == 409).toList();
        assertThat(conflicts).hasSize(9);
        assertThat(conflicts)
                .allSatisfy(r ->
                        assertThat(parseJson(r).get("code").asString())
                                .isEqualTo("ALREADY_COMPENSATED"));
        assertThat(balanceOf(accountId)).isEqualByComparingTo("100.00");
    }

    @Test
    void laMemeCleAvecDesCorpsDifferentsEnParalleleNeDonneQuUnSucces() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();
        var counter = new AtomicInteger(1);

        var responses =
                runRequests(
                        8,
                        () ->
                                send(
                                        "POST",
                                        "/accounts/" + accountId + "/deposits",
                                        token,
                                        "{\"amount\":\"" + counter.getAndIncrement() + ".00\"}",
                                        Map.of("Idempotency-Key", "cle-parallele")));

        assertThat(responses.stream().map(HttpResponse::statusCode).filter(s -> s == 201))
                .hasSize(1);
        var others = responses.stream().filter(r -> r.statusCode() != 201).toList();
        assertThat(others).hasSize(7);
        assertThat(others)
                .allSatisfy(r -> {
                    assertThat(r.statusCode()).isEqualTo(422);
                    assertThat(parseJson(r).get("code").asString())
                            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
                });
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM transactions WHERE account_id = ?::uuid",
                                Long.class,
                                accountId))
                .isEqualTo(1L);
    }

    /** Variante qui renvoie les réponses complètes. */
    private static List<HttpResponse<String>> runRequests(
            int count, Callable<HttpResponse<String>> call) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<HttpResponse<String>>>();
            for (int i = 0; i < count; i++) {
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

    private int withdraw(String token, String accountId, String key) {
        try {
            HttpResponse<String> response =
                    send(
                            "POST",
                            "/accounts/" + accountId + "/withdrawals",
                            token,
                            "{\"amount\":\"10.00\"}",
                            key == null ? Map.of() : Map.of("Idempotency-Key", key));
            return response.statusCode();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private BigDecimal balanceOf(String accountId) {
        return jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
    }

    /** Lance les appels au même instant sur un vrai pool de threads et renvoie les statuts. */
    private static List<Integer> runConcurrently(int count, IntSupplier call) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < count; i++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            return call.getAsInt();
                        }));
            }
            start.countDown();
            var statuses = new ArrayList<Integer>();
            for (var future : futures) {
                statuses.add(future.get());
            }
            return statuses;
        }
    }
}
