package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Un administrateur de démonstration (claim {@code demo} vrai) n'agit que sur les clients de
 * démonstration ; le vrai administrateur garde tous ses droits.
 */
class DemoAdminVisibilityIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    private final String restricted = createDemoToken(UUID.randomUUID(), ADMIN);
    private final String realAdmin = createToken(UUID.randomUUID(), ADMIN);

    /**
     * Client avec un compte EUR crédité de 500,00 ; {@code demo} le range parmi les clients démo.
     */
    private record Holder(UUID customerId, String token, String accountId, String iban) {}

    private Holder createHolder(boolean demo) throws Exception {
        var userId = UUID.randomUUID();
        var customer = createCustomerRef(userId, CustomerStatus.ACTIVE, demo);
        var token = createToken(userId, CLIENT);
        var account = parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));
        var accountId = account.get("id").asString();
        send("POST", "/accounts/" + accountId + "/deposits", token, "{\"amount\":\"500.00\"}");
        return new Holder(
                customer.getCustomerId(),
                token,
                accountId,
                account.get("accountNumber").asString());
    }

    private HttpResponse<String> deposit(String token, Holder holder, String amount)
            throws Exception {
        return send(
                "POST",
                "/accounts/" + holder.accountId() + "/deposits",
                token,
                "{\"amount\":\"" + amount + "\"}");
    }

    private HttpResponse<String> transfer(String token, Holder from, Holder to) throws Exception {
        return send(
                "POST",
                "/transfers",
                token,
                "{\"sourceAccountId\":\""
                        + from.accountId()
                        + "\",\"destinationIban\":\""
                        + to.iban()
                        + "\",\"amount\":\"10.00\"}",
                Map.of("Idempotency-Key", UUID.randomUUID().toString()));
    }

    private void withdrawAll(Holder holder) throws Exception {
        send(
                "POST",
                "/accounts/" + holder.accountId() + "/withdrawals",
                holder.token(),
                "{\"amount\":\"500.00\"}");
    }

    private String findLastTransactionId(Holder holder) {
        return jdbc.queryForObject(
                "SELECT id::text FROM transactions WHERE account_id = ?::uuid"
                        + " ORDER BY created_at DESC, id DESC LIMIT 1",
                String.class,
                holder.accountId());
    }

    private String codeOf(HttpResponse<String> response) {
        return parseJson(response).get("code").asString();
    }

    private void assertNotFound(HttpResponse<String> response, String code) {
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(codeOf(response)).isEqualTo(code);
    }

    @Test
    void laListeDesComptesEstFiltreeEnBasePourUnAdminDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        var onlyReal =
                parseJson(
                        send("GET", "/accounts?customerId=" + real.customerId(), restricted, null));
        var onlyDemo =
                parseJson(
                        send("GET", "/accounts?customerId=" + demo.customerId(), restricted, null));
        var all = parseJson(send("GET", "/accounts?size=100", restricted, null));
        var realAdminView =
                parseJson(
                        send("GET", "/accounts?customerId=" + real.customerId(), realAdmin, null));

        assertThat(onlyReal.get("totalElements").asLong()).isZero();
        assertThat(onlyDemo.get("totalElements").asLong()).isEqualTo(1);
        assertThat(realAdminView.get("totalElements").asLong()).isEqualTo(1);
        var demoAccounts =
                jdbc.queryForObject(
                        "SELECT count(*) FROM accounts a JOIN customer_refs c ON"
                                + " c.customer_id = a.customer_id WHERE c.demo",
                        Long.class);
        assertThat(all.get("totalElements").asLong()).isEqualTo(demoAccounts);
    }

    @Test
    void leDetailEtLeSoldeDUnCompteNonDemoSontIntrouvablesPourUnAdminDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        assertNotFound(
                send("GET", "/accounts/" + real.accountId(), restricted, null),
                "ACCOUNT_NOT_FOUND");
        assertNotFound(
                send("GET", "/accounts/" + real.accountId() + "/balance", restricted, null),
                "ACCOUNT_NOT_FOUND");
        assertThat(send("GET", "/accounts/" + demo.accountId(), restricted, null).statusCode())
                .isEqualTo(200);
        assertThat(
                        send("GET", "/accounts/" + demo.accountId() + "/balance", restricted, null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(send("GET", "/accounts/" + real.accountId(), realAdmin, null).statusCode())
                .isEqualTo(200);
    }

    @Test
    void laClotureNEstPermiseQuSurUnClientDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);
        withdrawAll(demo);
        withdrawAll(real);

        var refused = send("POST", "/accounts/" + real.accountId() + "/close", restricted, null);
        var allowed = send("POST", "/accounts/" + demo.accountId() + "/close", restricted, null);
        var byRealAdmin = send("POST", "/accounts/" + real.accountId() + "/close", realAdmin, null);

        assertNotFound(refused, "ACCOUNT_NOT_FOUND");
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(byRealAdmin.statusCode()).isEqualTo(200);
    }

    @Test
    void leDepotEtLeRetraitNeSontPermisQuSurUnClientDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);
        var withdrawal = "{\"amount\":\"5.00\"}";

        assertNotFound(deposit(restricted, real, "5.00"), "ACCOUNT_NOT_FOUND");
        assertNotFound(
                send(
                        "POST",
                        "/accounts/" + real.accountId() + "/withdrawals",
                        restricted,
                        withdrawal),
                "ACCOUNT_NOT_FOUND");
        assertThat(deposit(restricted, demo, "5.00").statusCode()).isEqualTo(201);
        assertThat(
                        send(
                                        "POST",
                                        "/accounts/" + demo.accountId() + "/withdrawals",
                                        restricted,
                                        withdrawal)
                                .statusCode())
                .isEqualTo(201);
        assertThat(deposit(realAdmin, real, "5.00").statusCode()).isEqualTo(201);
    }

    @Test
    void laCompensationNEstPermiseQuSurUneOperationDUnClientDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);
        var reason = "{\"reason\":\"Erreur de saisie\"}";
        var demoTransaction = findLastTransactionId(demo);
        var realTransaction = findLastTransactionId(real);

        var refused =
                send(
                        "POST",
                        "/transactions/" + realTransaction + "/compensations",
                        restricted,
                        reason);
        var allowed =
                send(
                        "POST",
                        "/transactions/" + demoTransaction + "/compensations",
                        restricted,
                        reason);

        assertNotFound(refused, "TRANSACTION_NOT_FOUND");
        assertThat(allowed.statusCode()).isEqualTo(201);
        var byRealAdmin =
                send(
                        "POST",
                        "/transactions/" + realTransaction + "/compensations",
                        realAdmin,
                        reason);
        assertThat(byRealAdmin.statusCode()).isEqualTo(201);
        assertLedgerInvariants(jdbc, demo.accountId(), real.accountId());
    }

    @Test
    void louvertureDUnCompteExigeUnClientDemoPourUnAdminDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        var refused =
                send(
                        "POST",
                        "/accounts",
                        restricted,
                        "{\"currency\":\"EUR\",\"customerId\":\"" + real.customerId() + "\"}");
        var allowed =
                send(
                        "POST",
                        "/accounts",
                        restricted,
                        "{\"currency\":\"EUR\",\"customerId\":\"" + demo.customerId() + "\"}");
        var byRealAdmin =
                send(
                        "POST",
                        "/accounts",
                        realAdmin,
                        "{\"currency\":\"EUR\",\"customerId\":\"" + real.customerId() + "\"}");

        assertNotFound(refused, "CUSTOMER_NOT_FOUND");
        assertThat(allowed.statusCode()).isEqualTo(201);
        assertThat(byRealAdmin.statusCode()).isEqualTo(201);
    }

    @Test
    void unVirementNePartQueDUnCompteDemoMaisPeutAllerVersNImporteQuelCompte() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        var fromReal = transfer(restricted, real, demo);
        var toReal = transfer(restricted, demo, real);
        var byRealAdmin = transfer(realAdmin, real, demo);

        assertNotFound(fromReal, "ACCOUNT_NOT_FOUND");
        assertThat(toReal.statusCode()).isEqualTo(201);
        assertThat(byRealAdmin.statusCode()).isEqualTo(201);
        assertLedgerInvariants(jdbc, demo.accountId(), real.accountId());
    }

    @Test
    void leDetailDUnVirementInvisibleEstIntrouvablePourUnAdminDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);
        var other = createHolder(false);
        var realOnly = parseJson(transfer(realAdmin, real, other)).get("id").asString();
        var fromDemo = parseJson(transfer(restricted, demo, real)).get("id").asString();

        var hidden = send("GET", "/transfers/" + realOnly, restricted, null);
        var visible = send("GET", "/transfers/" + fromDemo, restricted, null);
        var byRealAdmin = send("GET", "/transfers/" + realOnly, realAdmin, null);

        assertNotFound(hidden, "TRANSFER_NOT_FOUND");
        assertThat(visible.statusCode()).isEqualTo(200);
        assertThat(parseJson(visible).get("sourceAccountId").asString())
                .isEqualTo(demo.accountId());
        assertThat(byRealAdmin.statusCode()).isEqualTo(200);
    }

    @Test
    void lhistoriqueEstFiltreEnBasePourUnAdminDemo() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        var onlyReal =
                parseJson(
                        send(
                                "GET",
                                "/transactions?customerId=" + real.customerId(),
                                restricted,
                                null));
        var onlyDemo =
                parseJson(
                        send(
                                "GET",
                                "/transactions?customerId=" + demo.customerId(),
                                restricted,
                                null));
        var all = parseJson(send("GET", "/transactions?size=100", restricted, null));
        var realAdminView =
                parseJson(
                        send(
                                "GET",
                                "/transactions?customerId=" + real.customerId(),
                                realAdmin,
                                null));

        assertThat(onlyReal.get("totalElements").asLong()).isZero();
        assertThat(onlyDemo.get("totalElements").asLong()).isEqualTo(1);
        assertThat(realAdminView.get("totalElements").asLong()).isEqualTo(1);
        var demoTransactions =
                jdbc.queryForObject(
                        "SELECT count(*) FROM transactions t JOIN accounts a ON a.id = t.account_id"
                                + " JOIN customer_refs c ON c.customer_id = a.customer_id"
                                + " WHERE c.demo",
                        Long.class);
        assertThat(all.get("totalElements").asLong()).isEqualTo(demoTransactions);
    }

    @Test
    void unCompteOuUneOperationInvisibleSontIntrouvablesDansLhistorique() throws Exception {
        var demo = createHolder(true);
        var real = createHolder(false);

        assertNotFound(
                send("GET", "/transactions?accountId=" + real.accountId(), restricted, null),
                "ACCOUNT_NOT_FOUND");
        assertNotFound(
                send("GET", "/transactions/" + findLastTransactionId(real), restricted, null),
                "TRANSACTION_NOT_FOUND");
        assertThat(
                        send(
                                        "GET",
                                        "/transactions/" + findLastTransactionId(demo),
                                        restricted,
                                        null)
                                .statusCode())
                .isEqualTo(200);
        assertThat(
                        send("GET", "/transactions/" + findLastTransactionId(real), realAdmin, null)
                                .statusCode())
                .isEqualTo(200);
    }

    @Test
    void laVueDensembleResteGlobalePourUnAdminDemo() throws Exception {
        createHolder(false);
        createHolder(true);

        var summary = send("GET", "/accounts/summary", restricted, null);

        assertThat(summary.statusCode()).isEqualTo(200);
        var customers = parseJson(summary).get("customers").get("total").asLong();
        var known = jdbc.queryForObject("SELECT count(*) FROM customer_refs", Long.class);
        assertThat(customers).isEqualTo(known);
    }
}
