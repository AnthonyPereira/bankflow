package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

/** Les requêtes simultanées sont tranchées par les verrous, jamais par une erreur 500. */
class AccountConcurrencyIntegrationTests extends BankingApiTestSupport {

    @Test
    void deuxCloturesSimultaneesDonnentUn200EtUn409() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();

        var responses =
                runSimultaneously(
                        () -> send("POST", "/accounts/" + accountId + "/close", token, null),
                        () -> send("POST", "/accounts/" + accountId + "/close", token, null));

        assertThat(responses.stream().map(HttpResponse::statusCode))
                .containsExactlyInAnyOrder(200, 409);
        var conflict = responses.stream().filter(r -> r.statusCode() == 409).findFirst();
        assertThat(parseJson(conflict.orElseThrow()).get("code").asString())
                .isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void deuxOuverturesSimultanneesAuDelaDeCinqComptesDonnentUnSeul201() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        for (int i = 0; i < 4; i++) {
            assertThat(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}").statusCode())
                    .isEqualTo(201);
        }

        var responses =
                runSimultaneously(
                        () -> send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"),
                        () -> send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));

        assertThat(responses.stream().map(HttpResponse::statusCode))
                .containsExactlyInAnyOrder(201, 409);
        var accounts = parseJson(send("GET", "/accounts", token, null));
        assertThat(accounts.get("totalElements").asInt()).isEqualTo(5);
    }

    /** Lance les appels au même instant (barrière de départ) et renvoie leurs réponses. */
    @SafeVarargs
    private static List<HttpResponse<String>> runSimultaneously(
            Callable<HttpResponse<String>>... calls) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(calls.length)) {
            var futures =
                    java.util.Arrays.stream(calls)
                            .map(call ->
                                    executor.submit(() -> {
                                        start.await();
                                        return call.call();
                                    }))
                            .toList();
            start.countDown();
            var responses = new java.util.ArrayList<HttpResponse<String>>();
            for (var future : futures) {
                responses.add(future.get());
            }
            return responses;
        }
    }
}
