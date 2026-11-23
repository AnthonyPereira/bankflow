package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

class AccountApiIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void creerUnCompteAvantLaReferenceClientDonneCustomerNotReady() throws Exception {
        var token = createToken(UUID.randomUUID(), CLIENT);

        var response = createAccount(token, "EUR", null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("CUSTOMER_NOT_READY");
    }

    @Test
    void creerUnCompteRenvoie201AvecUnSoldeNulEtUnIbanValide() throws Exception {
        var userId = UUID.randomUUID();
        var customer = createCustomerRef(userId, CustomerStatus.ACTIVE);

        var response = createAccount(createToken(userId, CLIENT), "USD", "  Épargne  ");

        assertThat(response.statusCode()).isEqualTo(201);
        var account = parseJson(response);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/accounts/" + account.get("id").asString());
        assertThat(account.get("balance").asString()).isEqualTo("0.00");
        assertThat(account.get("currency").asString()).isEqualTo("USD");
        assertThat(account.get("label").asString()).isEqualTo("Épargne");
        assertThat(account.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(account.get("customerId").asString())
                .isEqualTo(customer.getCustomerId().toString());
        assertThat(account.get("closedAt").isNull()).isTrue();
        var iban = account.get("accountNumber").asString();
        assertThat(iban).hasSize(27).startsWith("FR");
        assertThat(AccountNumberGenerator.isValidIban(iban)).isTrue();
    }

    @Test
    void uneDeviseInconnueOuUnLibelleTropLongDonne400() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);

        assertThat(createAccount(token, "GBP", null).statusCode()).isEqualTo(400);
        var tooLong = createAccount(token, "EUR", "x".repeat(51));
        assertThat(tooLong.statusCode()).isEqualTo(400);
        assertThat(parseJson(tooLong).get("errors").get("label").asString()).isNotBlank();
    }

    @Test
    void unClientSuspenduNePeutPasOuvrirDeCompte() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.SUSPENDED);

        var response = createAccount(createToken(userId, CLIENT), "EUR", null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("CUSTOMER_SUSPENDED");
    }

    @Test
    void leSixiemeCompteActifEstRefuse() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        for (int i = 0; i < 5; i++) {
            assertThat(createAccount(token, "EUR", null).statusCode()).isEqualTo(201);
        }

        var response = createAccount(token, "EUR", null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(parseJson(response).get("code").asString()).isEqualTo("ACCOUNT_LIMIT_REACHED");
    }

    @Test
    void unCompteClotureLibereUneplaceSousLaLimite() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var first = parseJson(createAccount(token, "EUR", null)).get("id").asString();
        for (int i = 0; i < 4; i++) {
            createAccount(token, "EUR", null);
        }
        send("POST", "/accounts/" + first + "/close", token, null);

        assertThat(createAccount(token, "EUR", null).statusCode()).isEqualTo(201);
    }

    @Test
    void unClientNeVoitNiNeClotureLeCompteDUnAutre() throws Exception {
        var userA = UUID.randomUUID();
        var userB = UUID.randomUUID();
        createCustomerRef(userA, CustomerStatus.ACTIVE);
        createCustomerRef(userB, CustomerStatus.ACTIVE);
        var tokenA = createToken(userA, CLIENT);
        var tokenB = createToken(userB, CLIENT);
        var accountId = parseJson(createAccount(tokenA, "EUR", null)).get("id").asString();

        for (var request :
                new String[][] {
                    {"GET", "/accounts/" + accountId},
                    {"GET", "/accounts/" + accountId + "/balance"},
                    {"POST", "/accounts/" + accountId + "/close"}
                }) {
            var response = send(request[0], request[1], tokenB, null);
            assertThat(response.statusCode()).as(request[1]).isEqualTo(404);
            assertThat(parseJson(response).get("code").asString()).isEqualTo("ACCOUNT_NOT_FOUND");
        }
        assertThat(parseJson(send("GET", "/accounts", tokenB, null)).get("totalElements").asInt())
                .isZero();
        assertThat(send("GET", "/accounts/" + accountId, tokenA, null).statusCode()).isEqualTo(200);
    }

    @Test
    void unAdminListeEtLitTousLesComptes() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var accountId =
                parseJson(createAccount(createToken(userId, CLIENT), "EUR", null))
                        .get("id")
                        .asString();
        var adminToken = createToken(UUID.randomUUID(), ADMIN);

        var page = parseJson(send("GET", "/accounts?size=100", adminToken, null));

        assertThat(page.get("totalElements").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(page.get("content").valueStream().map(node -> node.get("id").asString()))
                .contains(accountId);
        assertThat(send("GET", "/accounts/" + accountId, adminToken, null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void unClientSansReferenceVoitUnePageVide() throws Exception {
        var response = send("GET", "/accounts", createToken(UUID.randomUUID(), CLIENT), null);

        assertThat(response.statusCode()).isEqualTo(200);
        var page = parseJson(response);
        assertThat(page.get("content")).isEmpty();
        assertThat(page.get("page").asInt()).isZero();
        assertThat(page.get("size").asInt()).isEqualTo(20);
        assertThat(page.get("totalPages").asInt()).isZero();
    }

    @Test
    void laPaginationRefuseUneTailleHorsLimites() throws Exception {
        var token = createToken(UUID.randomUUID(), CLIENT);

        assertThat(send("GET", "/accounts?size=101", token, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/accounts?size=0", token, null).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/accounts?page=-1", token, null).statusCode()).isEqualTo(400);
    }

    @Test
    void clotureUnCompteASoldeNulPuisRefuseUneSecondeCloture() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId = parseJson(createAccount(token, "EUR", null)).get("id").asString();

        var closed = send("POST", "/accounts/" + accountId + "/close", token, null);

        assertThat(closed.statusCode()).isEqualTo(200);
        assertThat(parseJson(closed).get("status").asString()).isEqualTo("CLOSED");
        assertThat(parseJson(closed).get("closedAt").isNull()).isFalse();
        var again = send("POST", "/accounts/" + accountId + "/close", token, null);
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(parseJson(again).get("code").asString()).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void clotureAvecUnSoldeNonNulEstRefusee() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId = parseJson(createAccount(token, "EUR", null)).get("id").asString();
        jdbc.update("UPDATE accounts SET balance = 12.5 WHERE id = ?::uuid", accountId);

        var response = send("POST", "/accounts/" + accountId + "/close", token, null);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(parseJson(response).get("code").asString())
                .isEqualTo("ACCOUNT_BALANCE_NOT_ZERO");
        var balance = parseJson(send("GET", "/accounts/" + accountId + "/balance", token, null));
        assertThat(balance.get("balance").asString()).isEqualTo("12.50");
    }

    @Test
    void leSoldeExposeLeCompteSaDeviseEtLaDateDeLecture() throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId = parseJson(createAccount(token, "USD", null)).get("id").asString();

        var response = send("GET", "/accounts/" + accountId + "/balance", token, null);

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode balance = parseJson(response);
        assertThat(balance.get("accountId").asString()).isEqualTo(accountId);
        assertThat(balance.get("balance").asString()).isEqualTo("0.00");
        assertThat(balance.get("currency").asString()).isEqualTo("USD");
        assertThat(balance.get("asOf").asString()).isNotBlank();
    }

    @Test
    void unClientSuspenduNePeutPasCloturerSonCompte() throws Exception {
        var userId = UUID.randomUUID();
        var customer = createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId = parseJson(createAccount(token, "EUR", null)).get("id").asString();
        customer.applyState(
                userId, customer.getEmail(), CustomerStatus.SUSPENDED, java.time.Instant.now());
        customerRefs.saveAndFlush(customer);

        var response = send("POST", "/accounts/" + accountId + "/close", token, null);

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(send("GET", "/accounts/" + accountId, token, null).statusCode()).isEqualTo(200);
    }

    @Test
    void uneRouteSansJwtEstRefusee() throws Exception {
        assertThat(send("GET", "/accounts", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void swaggerUiEstDisponibleSansJwt() throws Exception {
        assertThat(send("GET", "/swagger-ui/index.html", null, null).statusCode()).isEqualTo(200);
    }

    private HttpResponse<String> createAccount(String token, String currency, String label)
            throws Exception {
        var labelJson = label == null ? "" : ",\"label\":\"" + label + "\"";
        return send(
                "POST", "/accounts", token, "{\"currency\":\"" + currency + "\"" + labelJson + "}");
    }
}
