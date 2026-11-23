package com.bankflow.banking.api.operation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

class OperationApiIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    /** Client actif avec un compte EUR ; renvoie son jeton et l'identifiant du compte. */
    private record Holder(UUID userId, String token, String accountId) {}

    private Holder createHolder(CustomerStatus status) throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var accountId =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                        .get("id")
                        .asString();
        if (status == CustomerStatus.SUSPENDED) {
            jdbc.update("UPDATE customer_refs SET status = 'SUSPENDED' WHERE user_id = ?", userId);
        }
        return new Holder(userId, token, accountId);
    }

    private HttpResponse<String> operate(
            String token, String accountId, String kind, String body, String key) throws Exception {
        return send(
                "POST",
                "/accounts/" + accountId + "/" + kind,
                token,
                body,
                key == null ? Map.of() : Map.of("Idempotency-Key", key));
    }

    private HttpResponse<String> deposit(Holder holder, String amount) throws Exception {
        return operate(
                holder.token(),
                holder.accountId(),
                "deposits",
                "{\"amount\":\"" + amount + "\"}",
                null);
    }

    private String codeOf(HttpResponse<String> response) {
        return parseJson(response).get("code").asString();
    }

    @Test
    void unDepotCreditLeSoldeEtEcritLaTracabilite() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);

        var response =
                operate(
                        holder.token(),
                        holder.accountId(),
                        "deposits",
                        "{\"amount\":\"150.5\",\"description\":\"  Salaire  \"}",
                        null);

        assertThat(response.statusCode()).isEqualTo(201);
        var transaction = parseJson(response);
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/transactions/" + transaction.get("id").asString());
        assertThat(transaction.get("type").asString()).isEqualTo("DEPOSIT");
        assertThat(transaction.get("amount").asString()).isEqualTo("150.50");
        assertThat(transaction.get("balanceAfter").asString()).isEqualTo("150.50");
        assertThat(transaction.get("description").asString()).isEqualTo("Salaire");
        assertThat(transaction.get("performedBy").asString()).isEqualTo("CLIENT");
        assertThat(transaction.has("performedByUserId")).isFalse();
        var balance =
                parseJson(
                        send(
                                "GET",
                                "/accounts/" + holder.accountId() + "/balance",
                                holder.token(),
                                null));
        assertThat(balance.get("balance").asString()).isEqualTo("150.50");
        var entries =
                jdbc.queryForList(
                        "SELECT ledger_account, direction FROM ledger_entries WHERE transaction_id"
                                + " = ?::uuid",
                        transaction.get("id").asString());
        assertThat(entries)
                .extracting(row -> row.get("ledger_account") + ":" + row.get("direction"))
                .containsExactlyInAnyOrder("CUSTOMER_ACCOUNT:CREDIT", "CASH:DEBIT");
    }

    @Test
    void unRetraitDebiteLeSoldeEtUnSoldeInsuffisantEstRefuse() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        deposit(holder, "100.00");

        var ok =
                operate(
                        holder.token(),
                        holder.accountId(),
                        "withdrawals",
                        "{\"amount\":\"40.00\"}",
                        null);
        var tooMuch =
                operate(
                        holder.token(),
                        holder.accountId(),
                        "withdrawals",
                        "{\"amount\":\"60.01\"}",
                        null);

        assertThat(ok.statusCode()).isEqualTo(201);
        assertThat(parseJson(ok).get("balanceAfter").asString()).isEqualTo("60.00");
        assertThat(tooMuch.statusCode()).isEqualTo(409);
        assertThat(codeOf(tooMuch)).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void lePlafondEtLesDecimalesSontControles() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);

        assertThat(deposit(holder, "10000.00").statusCode()).isEqualTo(201);
        assertAmountError(
                deposit(holder, "10000.01"), "Le montant maximal par opération est de 10 000,00.");
        assertAmountError(deposit(holder, "1.234"), "Le montant a au plus 2 décimales.");
        assertAmountError(deposit(holder, "0"), "Le montant doit être positif.");
        assertAmountError(deposit(holder, "-5.00"), "Le montant doit être positif.");
        var missing = operate(holder.token(), holder.accountId(), "deposits", "{}", null);
        assertThat(missing.statusCode()).isEqualTo(400);
        assertThat(parseJson(missing).get("errors").has("amount")).isTrue();
    }

    private void assertAmountError(HttpResponse<String> response, String message) {
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(codeOf(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(parseJson(response).get("errors").get("amount").asString()).isEqualTo(message);
    }

    @Test
    void unAdminDeposeAuNomDUnClient() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);

        var response =
                operate(adminToken, holder.accountId(), "deposits", "{\"amount\":\"20.00\"}", null);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("performedBy").asString()).isEqualTo("ADMIN");
    }

    @Test
    void unClientNeTouchePasAuCompteDUnAutre() throws Exception {
        var owner = createHolder(CustomerStatus.ACTIVE);
        var other = createHolder(CustomerStatus.ACTIVE);

        for (var kind : new String[] {"deposits", "withdrawals"}) {
            var response =
                    operate(other.token(), owner.accountId(), kind, "{\"amount\":\"5.00\"}", null);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(codeOf(response)).isEqualTo("ACCOUNT_NOT_FOUND");
        }
        var unknown =
                operate(
                        owner.token(),
                        UUID.randomUUID().toString(),
                        "deposits",
                        "{\"amount\":\"5.00\"}",
                        null);
        assertThat(unknown.statusCode()).isEqualTo(404);
    }

    @Test
    void unClientSuspenduEtUnCompteClotureRefusentLesOperations() throws Exception {
        var suspended = createHolder(CustomerStatus.SUSPENDED);
        var response = deposit(suspended, "5.00");
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo("CUSTOMER_SUSPENDED");
        var adminResponse =
                operate(
                        createToken(UUID.randomUUID(), ADMIN),
                        suspended.accountId(),
                        "deposits",
                        "{\"amount\":\"5.00\"}",
                        null);
        assertThat(adminResponse.statusCode()).isEqualTo(403);

        var closed = createHolder(CustomerStatus.ACTIVE);
        send("POST", "/accounts/" + closed.accountId() + "/close", closed.token(), null);
        var onClosed = deposit(closed, "5.00");
        assertThat(onClosed.statusCode()).isEqualTo(409);
        assertThat(codeOf(onClosed)).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void lAuthentificationEstObligatoire() throws Exception {
        var response = send("POST", "/accounts/" + UUID.randomUUID() + "/deposits", null, "{}");
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void laMemeCleRejoueLaMemeReponseSansRefaireLOperation() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var body = "{\"amount\":\"30.00\",\"description\":\"Cadeau\"}";

        var first = operate(holder.token(), holder.accountId(), "deposits", body, "cle-1");
        var second = operate(holder.token(), holder.accountId(), "deposits", body, "cle-1");

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(first.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.headers().firstValue("Idempotent-Replayed")).contains("true");
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(second.headers().firstValue("Location"))
                .isEqualTo(first.headers().firstValue("Location"));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM transactions WHERE account_id = ?::uuid",
                                Long.class,
                                holder.accountId()))
                .isEqualTo(1L);
    }

    @Test
    void laMemeCleAvecUnCorpsDifferentDonne422() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        operate(holder.token(), holder.accountId(), "deposits", "{\"amount\":\"30.00\"}", "cle-2");

        var response =
                operate(
                        holder.token(),
                        holder.accountId(),
                        "deposits",
                        "{\"amount\":\"31.00\"}",
                        "cle-2");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(codeOf(response)).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void laCleEstPropreAChaquePersonneEtUneErreurMetierLaLibere() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        var body = "{\"amount\":\"30.00\"}";

        var failed = operate(holder.token(), holder.accountId(), "withdrawals", body, "cle-3");
        deposit(holder, "100.00");
        var retried = operate(holder.token(), holder.accountId(), "withdrawals", body, "cle-3");
        var otherActor = operate(adminToken, holder.accountId(), "withdrawals", body, "cle-3");

        assertThat(failed.statusCode()).isEqualTo(409);
        assertThat(retried.statusCode()).isEqualTo(201);
        assertThat(otherActor.statusCode()).isEqualTo(201);
        assertThat(otherActor.headers().firstValue("Idempotent-Replayed")).isEmpty();
    }

    @Test
    void uneCleAuFormatInvalideDonne400() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);

        var response =
                operate(
                        holder.token(),
                        holder.accountId(),
                        "deposits",
                        "{\"amount\":\"1.00\"}",
                        "cle invalide!");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(codeOf(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(parseJson(response).get("errors").has("Idempotency-Key")).isTrue();
    }

    @Test
    void lHistoriqueEstFiltreEtPagine() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var other = createHolder(CustomerStatus.ACTIVE);
        deposit(holder, "10.00");
        deposit(holder, "20.00");
        operate(holder.token(), holder.accountId(), "withdrawals", "{\"amount\":\"5.00\"}", null);
        deposit(other, "99.00");

        var all = parseJson(send("GET", "/transactions?size=2", holder.token(), null));
        var deposits =
                parseJson(
                        send(
                                "GET",
                                "/transactions?type=DEPOSIT&accountId=" + holder.accountId(),
                                holder.token(),
                                null));
        var today = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Paris")).toString();
        var period =
                parseJson(
                        send(
                                "GET",
                                "/transactions?from=" + today + "&to=" + today,
                                holder.token(),
                                null));

        assertThat(all.get("totalElements").asInt()).isEqualTo(3);
        assertThat(all.get("totalPages").asInt()).isEqualTo(2);
        assertThat(all.get("content")).hasSize(2);
        assertThat(all.get("content").get(0).get("type").asString()).isEqualTo("WITHDRAWAL");
        assertThat(deposits.get("totalElements").asInt()).isEqualTo(2);
        assertThat(period.get("totalElements").asInt()).isEqualTo(3);
        var otherAccount =
                send("GET", "/transactions?accountId=" + other.accountId(), holder.token(), null);
        assertThat(otherAccount.statusCode()).isEqualTo(404);
        assertThat(codeOf(otherAccount)).isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void lesParametresInvalidesDeLHistoriqueDonnent400() throws Exception {
        var token = createToken(UUID.randomUUID(), CLIENT);

        for (var query :
                new String[] {
                    "from=2026-02-01&to=2026-01-01",
                    "size=101",
                    "page=-1",
                    "type=AUTRE",
                    "from=hier"
                }) {
            var response = send("GET", "/transactions?" + query, token, null);
            assertThat(response.statusCode()).as(query).isEqualTo(400);
            assertThat(codeOf(response)).as(query).isEqualTo("VALIDATION_ERROR");
        }
    }

    @Test
    void unAdminVoitToutEtFiltreParClient() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var other = createHolder(CustomerStatus.ACTIVE);
        deposit(holder, "10.00");
        deposit(other, "20.00");
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        var customerId =
                parseJson(send("GET", "/accounts/" + holder.accountId(), adminToken, null))
                        .get("customerId")
                        .asString();

        var filtered =
                parseJson(send("GET", "/transactions?customerId=" + customerId, adminToken, null));
        var everything = parseJson(send("GET", "/transactions?size=100", adminToken, null));

        assertThat(filtered.get("totalElements").asInt()).isEqualTo(1);
        assertThat(everything.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
        // Un client ignore customerId
        var ignored =
                parseJson(
                        send(
                                "GET",
                                "/transactions?customerId=" + UUID.randomUUID(),
                                holder.token(),
                                null));
        assertThat(ignored.get("totalElements").asInt()).isEqualTo(1);
    }

    @Test
    void uneOperationEstVisibleDeSonTitulaireEtDeLAdminSeulement() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var other = createHolder(CustomerStatus.ACTIVE);
        var id = parseJson(deposit(holder, "10.00")).get("id").asString();

        assertThat(send("GET", "/transactions/" + id, holder.token(), null).statusCode())
                .isEqualTo(200);
        assertThat(
                        send(
                                        "GET",
                                        "/transactions/" + id,
                                        createToken(UUID.randomUUID(), ADMIN),
                                        null)
                                .statusCode())
                .isEqualTo(200);
        var foreign = send("GET", "/transactions/" + id, other.token(), null);
        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(codeOf(foreign)).isEqualTo("TRANSACTION_NOT_FOUND");
        assertThat(
                        send("GET", "/transactions/" + UUID.randomUUID(), holder.token(), null)
                                .statusCode())
                .isEqualTo(404);
    }

    @Test
    void unAdminAnnuleUnDepotEtUneSecondeAnnulationEstRefusee() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        var id = parseJson(deposit(holder, "50.00")).get("id").asString();
        var reason = "{\"reason\":\"  Erreur de saisie du conseiller  \"}";

        var first = send("POST", "/transactions/" + id + "/compensations", adminToken, reason);
        var second = send("POST", "/transactions/" + id + "/compensations", adminToken, reason);

        assertThat(first.statusCode()).isEqualTo(201);
        var compensation = parseJson(first);
        assertThat(compensation.get("type").asString()).isEqualTo("COMPENSATION");
        assertThat(compensation.get("amount").asString()).isEqualTo("50.00");
        assertThat(compensation.get("balanceAfter").asString()).isEqualTo("0.00");
        assertThat(compensation.get("compensatesTransactionId").asString()).isEqualTo(id);
        assertThat(compensation.get("description").asString())
                .isEqualTo("Erreur de saisie du conseiller");
        assertThat(compensation.get("performedBy").asString()).isEqualTo("ADMIN");
        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(codeOf(second)).isEqualTo("ALREADY_COMPENSATED");
        var original = parseJson(send("GET", "/transactions/" + id, holder.token(), null));
        assertThat(original.get("compensatedByTransactionId").asString())
                .isEqualTo(compensation.get("id").asString());
    }

    @Test
    void annulerUnRetraitRecrediteLeCompte() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        deposit(holder, "80.00");
        var withdrawal =
                parseJson(
                                operate(
                                        holder.token(),
                                        holder.accountId(),
                                        "withdrawals",
                                        "{\"amount\":\"30.00\"}",
                                        null))
                        .get("id")
                        .asString();

        var response =
                send(
                        "POST",
                        "/transactions/" + withdrawal + "/compensations",
                        adminToken,
                        "{\"reason\":\"Retrait contesté\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("balanceAfter").asString()).isEqualTo("80.00");
    }

    @Test
    void annulerUnDepotDejaDepenseDonneSoldeInsuffisant() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var id = parseJson(deposit(holder, "50.00")).get("id").asString();
        operate(holder.token(), holder.accountId(), "withdrawals", "{\"amount\":\"40.00\"}", null);

        var response =
                send(
                        "POST",
                        "/transactions/" + id + "/compensations",
                        createToken(UUID.randomUUID(), ADMIN),
                        "{\"reason\":\"Dépôt erroné\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("INSUFFICIENT_FUNDS");
    }

    @Test
    void uneCompensationNeSeCompensePasEtUnClientNePeutPasCompenser() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);
        var id = parseJson(deposit(holder, "50.00")).get("id").asString();

        var asClient =
                send(
                        "POST",
                        "/transactions/" + id + "/compensations",
                        holder.token(),
                        "{\"reason\":\"Je veux annuler\"}");
        var clientInvalidBody =
                send(
                        "POST",
                        "/transactions/" + id + "/compensations",
                        holder.token(),
                        "{\"reason\":\"x\"}");
        var compensation =
                parseJson(
                                send(
                                        "POST",
                                        "/transactions/" + id + "/compensations",
                                        adminToken,
                                        "{\"reason\":\"Erreur de saisie\"}"))
                        .get("id")
                        .asString();
        var ofCompensation =
                send(
                        "POST",
                        "/transactions/" + compensation + "/compensations",
                        adminToken,
                        "{\"reason\":\"Encore une annulation\"}");
        var unknown =
                send(
                        "POST",
                        "/transactions/" + UUID.randomUUID() + "/compensations",
                        adminToken,
                        "{\"reason\":\"Erreur de saisie\"}");
        var shortReason =
                send(
                        "POST",
                        "/transactions/" + id + "/compensations",
                        adminToken,
                        "{\"reason\":\"court\"}");

        assertThat(asClient.statusCode()).isEqualTo(403);
        assertThat(codeOf(asClient)).isEqualTo("ACCESS_DENIED");
        assertThat(clientInvalidBody.statusCode()).isEqualTo(403);
        assertThat(ofCompensation.statusCode()).isEqualTo(409);
        assertThat(codeOf(ofCompensation)).isEqualTo("COMPENSATION_NOT_ALLOWED");
        assertThat(unknown.statusCode()).isEqualTo(404);
        assertThat(codeOf(unknown)).isEqualTo("TRANSACTION_NOT_FOUND");
        assertThat(shortReason.statusCode()).isEqualTo(400);
        assertThat(parseJson(shortReason).get("errors").has("reason")).isTrue();
    }

    @Test
    void unVirementNeSeCompensePas() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        var accountId = UUID.fromString(holder.accountId());
        var transferId = UUID.randomUUID();
        var otherAccount = UUID.fromString(createHolder(CustomerStatus.ACTIVE).accountId());
        jdbc.update(
                "INSERT INTO transfers (id, source_account_id, destination_account_id, amount,"
                    + " currency, status, created_by_customer_id, created_at, performed_by_user_id,"
                    + " performed_by_role) VALUES (?, ?, ?, 1, 'EUR', 'COMPLETED', ?, now(), ?,"
                    + " 'CLIENT')",
                transferId,
                accountId,
                otherAccount,
                UUID.randomUUID(),
                holder.userId());
        var transactionId = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO transactions (id, account_id, type, amount, currency, balance_after,"
                    + " transfer_id, created_at, performed_by_user_id, performed_by_role) VALUES"
                    + " (?, ?, 'TRANSFER_OUT', 1, 'EUR', 0, ?, now(), ?, 'CLIENT')",
                transactionId,
                accountId,
                transferId,
                holder.userId());

        var response =
                send(
                        "POST",
                        "/transactions/" + transactionId + "/compensations",
                        createToken(UUID.randomUUID(), ADMIN),
                        "{\"reason\":\"Virement erroné\"}");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("COMPENSATION_NOT_ALLOWED");
    }

    @Test
    void lesEcrituresSontImmuablesEtLesComptesSontEquilibres() throws Exception {
        var holder = createHolder(CustomerStatus.ACTIVE);
        deposit(holder, "70.00");
        operate(holder.token(), holder.accountId(), "withdrawals", "{\"amount\":\"20.00\"}", null);

        for (var sql :
                new String[] {
                    "UPDATE transactions SET description = 'x'",
                    "DELETE FROM transactions",
                    "UPDATE ledger_entries SET amount = 1",
                    "DELETE FROM ledger_entries",
                    "TRUNCATE transactions CASCADE",
                    "TRUNCATE ledger_entries"
                }) {
            assertThatThrownBy(() -> jdbc.update(sql))
                    .as(sql)
                    .isInstanceOf(DataAccessException.class)
                    .hasMessageContaining("immuables");
        }
        var accountBalance =
                jdbc.queryForObject(
                        "SELECT balance FROM accounts WHERE id = ?::uuid",
                        java.math.BigDecimal.class,
                        holder.accountId());
        var ledgerBalance =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount ELSE -amount"
                                + " END), 0) FROM ledger_entries WHERE account_id = ?::uuid",
                        java.math.BigDecimal.class,
                        holder.accountId());
        var imbalance =
                jdbc.queryForObject(
                        "SELECT COALESCE(SUM(CASE direction WHEN 'DEBIT' THEN amount ELSE -amount"
                                + " END), 0) FROM ledger_entries",
                        java.math.BigDecimal.class);
        assertThat(accountBalance).isEqualByComparingTo("50.00");
        assertThat(ledgerBalance).isEqualByComparingTo(accountBalance);
        assertThat(imbalance).isEqualByComparingTo("0");
    }
}
