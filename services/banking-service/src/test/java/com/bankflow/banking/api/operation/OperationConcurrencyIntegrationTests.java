package com.bankflow.banking.api.operation;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
    private static java.util.List<Integer> runConcurrently(
            int count, java.util.function.IntSupplier call) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var futures = new ArrayList<java.util.concurrent.Future<Integer>>();
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
