package com.bankflow.banking.api.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class TransferApiIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    /** Client actif avec un compte ouvert dans la devise donnée. */
    private record Holder(UUID userId, String token, String accountId, String iban) {}

    private Holder createHolder(String currency, String deposit) throws Exception {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);
        var account =
                parseJson(send("POST", "/accounts", token, "{\"currency\":\"" + currency + "\"}"));
        var accountId = account.get("id").asString();
        if (deposit != null) {
            send(
                    "POST",
                    "/accounts/" + accountId + "/deposits",
                    token,
                    "{\"amount\":\"" + deposit + "\"}");
        }
        return new Holder(userId, token, accountId, account.get("accountNumber").asString());
    }

    private HttpResponse<String> transfer(
            String token, String sourceId, String iban, String amount, String key)
            throws Exception {
        var body =
                "{\"sourceAccountId\":\""
                        + sourceId
                        + "\",\"destinationIban\":\""
                        + iban
                        + "\",\"amount\":\""
                        + amount
                        + "\"}";
        return send(
                "POST",
                "/transfers",
                token,
                body,
                key == null ? Map.of() : Map.of("Idempotency-Key", key));
    }

    private HttpResponse<String> transfer(Holder from, Holder to, String amount) throws Exception {
        return transfer(
                from.token(), from.accountId(), to.iban(), amount, UUID.randomUUID().toString());
    }

    private BigDecimal balanceOf(String accountId) {
        return jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
    }

    private long countTransfersFrom(String accountId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM transfers WHERE source_account_id = ?::uuid",
                Long.class,
                accountId);
    }

    private String codeOf(HttpResponse<String> response) {
        return parseJson(response).get("code").asString();
    }

    @Test
    void unVirementDeCinqCentsDebiteLaSourceCrediteLaDestinationEtEcritLesEcritures()
            throws Exception {
        var source = createHolder("EUR", "1000.00");
        var destination = createHolder("EUR", "50.00");

        var response =
                transfer(
                        source.token(),
                        source.accountId(),
                        destination.iban().substring(0, 4)
                                + " "
                                + destination.iban().substring(4).toLowerCase(),
                        "500.00",
                        "virement-1");

        assertThat(response.statusCode()).isEqualTo(201);
        var transfer = parseJson(response);
        var transferId = transfer.get("id").asString();
        assertThat(response.headers().firstValue("Location").orElseThrow())
                .endsWith("/transfers/" + transferId);
        assertThat(transfer.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(transfer.get("amount").asString()).isEqualTo("500.00");
        assertThat(transfer.get("sourceIban").asString()).isEqualTo(source.iban());
        assertThat(transfer.get("destinationIban").asString()).isEqualTo(destination.iban());
        assertThat(transfer.get("performedBy").asString()).isEqualTo("CLIENT");
        assertThat(transfer.get("completedAt").isNull()).isFalse();
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("500.00");
        assertThat(balanceOf(destination.accountId())).isEqualByComparingTo("550.00");
        var legs =
                jdbc.queryForList(
                        "SELECT type, account_id::text AS account_id, balance_after FROM"
                                + " transactions WHERE transfer_id = ?::uuid",
                        transferId);
        assertThat(legs).hasSize(2);
        assertThat(legs)
                .extracting(row -> row.get("type") + ":" + row.get("account_id"))
                .containsExactlyInAnyOrder(
                        "TRANSFER_OUT:" + source.accountId(),
                        "TRANSFER_IN:" + destination.accountId());
        var entries =
                jdbc.queryForList(
                        "SELECT e.ledger_account, e.direction, e.account_id::text AS account_id"
                                + " FROM ledger_entries e JOIN transactions t ON t.id ="
                                + " e.transaction_id WHERE t.transfer_id = ?::uuid",
                        transferId);
        assertThat(entries)
                .extracting(row ->
                        row.get("ledger_account")
                                + ":"
                                + row.get("direction")
                                + ":"
                                + row.get("account_id"))
                .containsExactlyInAnyOrder(
                        "CUSTOMER_ACCOUNT:DEBIT:" + source.accountId(),
                        "CUSTOMER_ACCOUNT:CREDIT:" + destination.accountId());
        assertLedgerInvariants(jdbc, source.accountId(), destination.accountId());
    }

    @Test
    void unAdminVirementDepuisLeCompteDUnClientEtLaTracabiliteLeMontre() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);

        var response =
                transfer(adminToken, source.accountId(), destination.iban(), "30.00", "cle-admin");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("performedBy").asString()).isEqualTo("ADMIN");
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("70.00");
        var createdBy =
                jdbc.queryForObject(
                        "SELECT created_by_customer_id::text FROM transfers WHERE id = ?::uuid",
                        String.class,
                        parseJson(response).get("id").asString());
        var sourceCustomer =
                jdbc.queryForObject(
                        "SELECT customer_id::text FROM accounts WHERE id = ?::uuid",
                        String.class,
                        source.accountId());
        assertThat(createdBy).isEqualTo(sourceCustomer);
    }

    @Test
    void laCleDIdempotenceEstObligatoire() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);

        var response =
                transfer(source.token(), source.accountId(), destination.iban(), "10.00", null);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(codeOf(response)).isEqualTo("VALIDATION_ERROR");
        assertThat(parseJson(response).get("errors").get("Idempotency-Key").asString())
                .isEqualTo("La clé d'idempotence est obligatoire.");
        assertThat(countTransfersFrom(source.accountId())).isZero();
    }

    @Test
    void lesChampsInvalidesSontRefuses() throws Exception {
        var source = createHolder("EUR", "100.00");

        var badAmount = transfer(source.token(), source.accountId(), "FR76X", "10000.01", "k1");
        var noIban = transfer(source.token(), source.accountId(), " ", "10.00", "k2");
        var noSource =
                send(
                        "POST",
                        "/transfers",
                        source.token(),
                        "{\"destinationIban\":\"FR76X\",\"amount\":\"1.00\"}",
                        Map.of("Idempotency-Key", "k3"));

        assertThat(badAmount.statusCode()).isEqualTo(400);
        assertThat(parseJson(badAmount).get("errors").get("amount").asString())
                .isEqualTo("Le montant maximal par opération est de 10 000,00.");
        assertThat(noIban.statusCode()).isEqualTo(400);
        assertThat(parseJson(noIban).get("errors").has("destinationIban")).isTrue();
        assertThat(noSource.statusCode()).isEqualTo(400);
        assertThat(parseJson(noSource).get("errors").has("sourceAccountId")).isTrue();
    }

    @Test
    void lesCodesDErreurMetierSontRenvoyesDansLOrdreDeLaSpecification() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        var other = createHolder("EUR", "100.00");
        var dollars = createHolder("USD", null);
        var closed = createHolder("EUR", null);
        send("POST", "/accounts/" + closed.accountId() + "/close", closed.token(), null);

        var unknownSource =
                transfer(
                        source.token(),
                        UUID.randomUUID().toString(),
                        destination.iban(),
                        "1.00",
                        "e1");
        var foreignSource =
                transfer(source.token(), other.accountId(), destination.iban(), "1.00", "e2");
        var unknownIban =
                transfer(
                        source.token(),
                        source.accountId(),
                        "FR7699999000010000000000000",
                        "1.00",
                        "e3");
        var closedDestination =
                transfer(source.token(), source.accountId(), closed.iban(), "1.00", "e4");
        var sameAccount = transfer(source.token(), source.accountId(), source.iban(), "1.00", "e5");
        var currency = transfer(source.token(), source.accountId(), dollars.iban(), "1.00", "e6");
        var funds =
                transfer(source.token(), source.accountId(), destination.iban(), "100.01", "e7");

        assertThat(unknownSource.statusCode()).isEqualTo(404);
        assertThat(codeOf(unknownSource)).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(foreignSource.statusCode()).isEqualTo(404);
        assertThat(codeOf(foreignSource)).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(unknownIban.statusCode()).isEqualTo(404);
        assertThat(codeOf(unknownIban)).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
        assertThat(parseJson(unknownIban).get("detail").asString())
                .isEqualTo("Aucun compte actif ne correspond à cet IBAN.");
        assertThat(closedDestination.statusCode()).isEqualTo(404);
        assertThat(codeOf(closedDestination)).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
        assertThat(sameAccount.statusCode()).isEqualTo(400);
        assertThat(codeOf(sameAccount)).isEqualTo("VALIDATION_ERROR");
        assertThat(parseJson(sameAccount).get("errors").get("destinationIban").asString())
                .isEqualTo("Le compte de destination doit être différent du compte source.");
        assertThat(currency.statusCode()).isEqualTo(409);
        assertThat(codeOf(currency)).isEqualTo("CURRENCY_MISMATCH");
        assertThat(funds.statusCode()).isEqualTo(409);
        assertThat(codeOf(funds)).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(countTransfersFrom(source.accountId())).isZero();
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void uneSourceClotureeEstRefusee() throws Exception {
        var source = createHolder("EUR", null);
        var destination = createHolder("EUR", null);
        send("POST", "/accounts/" + source.accountId() + "/close", source.token(), null);

        var response = transfer(source, destination, "1.00");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(codeOf(response)).isEqualTo("ACCOUNT_CLOSED");
    }

    @Test
    void unClientSuspenduNePeutPasVirerMaisPeutRecevoir() throws Exception {
        var suspended = createHolder("EUR", "100.00");
        var active = createHolder("EUR", "100.00");
        jdbc.update(
                "UPDATE customer_refs SET status = 'SUSPENDED' WHERE user_id = ?",
                suspended.userId());

        var refused = transfer(suspended, active, "10.00");
        var received = transfer(active, suspended, "10.00");

        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(codeOf(refused)).isEqualTo("CUSTOMER_SUSPENDED");
        assertThat(received.statusCode()).isEqualTo(201);
        assertThat(balanceOf(suspended.accountId())).isEqualByComparingTo("110.00");
    }

    @Test
    void rejouerLaMemeCleRenvoieLaMemeReponseSansSecondVirement() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);

        var first =
                transfer(source.token(), source.accountId(), destination.iban(), "40.00", "rejeu");
        var second =
                transfer(source.token(), source.accountId(), destination.iban(), "40.00", "rejeu");

        assertThat(first.statusCode()).isEqualTo(201);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(countTransfersFrom(source.accountId())).isEqualTo(1);
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("60.00");
    }

    @Test
    void laMemeCleAvecUnAutreCorpsEstRefusee() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        transfer(source.token(), source.accountId(), destination.iban(), "40.00", "autre");

        var response =
                transfer(source.token(), source.accountId(), destination.iban(), "41.00", "autre");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(codeOf(response)).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void leVirementEstVisiblePourLaSourceLaDestinationEtUnAdminMaisPasUnTiers() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        var third = createHolder("EUR", null);
        var created = parseJson(transfer(source, destination, "20.00"));
        var path = "/transfers/" + created.get("id").asString();

        var bySource = send("GET", path, source.token(), null);
        var byDestination = send("GET", path, destination.token(), null);
        var byThird = send("GET", path, third.token(), null);
        var byAdmin = send("GET", path, createToken(UUID.randomUUID(), ADMIN), null);
        var unknown = send("GET", "/transfers/" + UUID.randomUUID(), source.token(), null);

        assertThat(bySource.statusCode()).isEqualTo(200);
        assertThat(parseJson(bySource).get("sourceAccountId").asString())
                .isEqualTo(source.accountId());
        assertThat(byDestination.statusCode()).isEqualTo(200);
        assertThat(parseJson(byDestination).get("sourceAccountId").isNull()).isTrue();
        assertThat(parseJson(byDestination).get("sourceIban").asString()).isEqualTo(source.iban());
        assertThat(parseJson(byDestination).get("destinationAccountId").asString())
                .isEqualTo(destination.accountId());
        assertThat(byThird.statusCode()).isEqualTo(404);
        assertThat(codeOf(byThird)).isEqualTo("TRANSFER_NOT_FOUND");
        assertThat(byAdmin.statusCode()).isEqualTo(200);
        assertThat(parseJson(byAdmin).get("sourceAccountId").asString())
                .isEqualTo(source.accountId());
        assertThat(codeOf(unknown)).isEqualTo("TRANSFER_NOT_FOUND");
    }

    @Test
    void lHistoriqueIndiqueLIbanDeLaContrepartie() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        transfer(source, destination, "20.00");

        var sourceHistory =
                parseJson(
                        send(
                                "GET",
                                "/transactions?accountId=" + source.accountId(),
                                source.token(),
                                null));
        var destinationHistory =
                parseJson(
                        send(
                                "GET",
                                "/transactions?accountId=" + destination.accountId(),
                                destination.token(),
                                null));

        var out = sourceHistory.get("content").get(0);
        assertThat(out.get("type").asString()).isEqualTo("TRANSFER_OUT");
        assertThat(out.get("direction").asString()).isEqualTo("DEBIT");
        assertThat(out.get("counterpartyIban").asString()).isEqualTo(destination.iban());
        assertThat(out.get("transferId").isNull()).isFalse();
        var in = destinationHistory.get("content").get(0);
        assertThat(in.get("type").asString()).isEqualTo("TRANSFER_IN");
        assertThat(in.get("counterpartyIban").asString()).isEqualTo(source.iban());
        var deposit = sourceHistory.get("content").get(1);
        assertThat(deposit.get("type").asString()).isEqualTo("DEPOSIT");
        assertThat(deposit.get("counterpartyIban").isNull()).isTrue();
    }

    @Test
    void sansJetonLesRoutesDeVirementRepondent401() throws Exception {
        var post =
                send(
                        "POST",
                        "/transfers",
                        null,
                        "{\"sourceAccountId\":\"" + UUID.randomUUID() + "\"}",
                        Map.of("Idempotency-Key", "k"));
        var get = send("GET", "/transfers/" + UUID.randomUUID(), null, null);

        assertThat(post.statusCode()).isEqualTo(401);
        assertThat(get.statusCode()).isEqualTo(401);
    }

    @Test
    void unClientSuspenduAvecUnIbanInconnuRecoitUn404AvantLe403() throws Exception {
        var suspended = createHolder("EUR", "100.00");
        jdbc.update(
                "UPDATE customer_refs SET status = 'SUSPENDED' WHERE user_id = ?",
                suspended.userId());

        var response =
                transfer(
                        suspended.token(),
                        suspended.accountId(),
                        "FR7699999000010000000000000",
                        "1.00",
                        "ordre-1");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(codeOf(response)).isEqualTo("DESTINATION_ACCOUNT_NOT_FOUND");
    }

    @Test
    void unAdminNePeutPasVirerDepuisLeCompteDUnClientSuspendu() throws Exception {
        var suspended = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        jdbc.update(
                "UPDATE customer_refs SET status = 'SUSPENDED' WHERE user_id = ?",
                suspended.userId());

        var response =
                transfer(
                        createToken(UUID.randomUUID(), ADMIN),
                        suspended.accountId(),
                        destination.iban(),
                        "10.00",
                        "admin-suspendu");

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(codeOf(response)).isEqualTo("CUSTOMER_SUSPENDED");
        assertThat(balanceOf(suspended.accountId())).isEqualByComparingTo("100.00");
    }

    @Test
    void laMemeCleEnvoyeeParDeuxActeursDonneDeuxVirementsDistincts() throws Exception {
        var source = createHolder("EUR", "100.00");
        var destination = createHolder("EUR", null);
        var adminToken = createToken(UUID.randomUUID(), ADMIN);

        var byClient =
                transfer(
                        source.token(),
                        source.accountId(),
                        destination.iban(),
                        "10.00",
                        "partagee");
        var byAdmin =
                transfer(adminToken, source.accountId(), destination.iban(), "10.00", "partagee");

        assertThat(byClient.statusCode()).isEqualTo(201);
        assertThat(byAdmin.statusCode()).isEqualTo(201);
        assertThat(byAdmin.headers().firstValue("Idempotent-Replayed")).isEmpty();
        assertThat(parseJson(byAdmin).get("id").asString())
                .isNotEqualTo(parseJson(byClient).get("id").asString());
        assertThat(countTransfersFrom(source.accountId())).isEqualTo(2);
        assertThat(balanceOf(source.accountId())).isEqualByComparingTo("80.00");
    }

    @Test
    void unVirementEntreDeuxComptesDuMemeClientMontreLaSourceAuTitulaire() throws Exception {
        var first = createHolder("EUR", "100.00");
        var secondAccount =
                parseJson(send("POST", "/accounts", first.token(), "{\"currency\":\"EUR\"}"));

        var response =
                transfer(
                        first.token(),
                        first.accountId(),
                        secondAccount.get("accountNumber").asString(),
                        "25.00",
                        "interne");
        var read =
                send(
                        "GET",
                        "/transfers/" + parseJson(response).get("id").asString(),
                        first.token(),
                        null);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(read).get("sourceAccountId").asString()).isEqualTo(first.accountId());
        assertThat(parseJson(read).get("destinationAccountId").asString())
                .isEqualTo(secondAccount.get("id").asString());
    }
}
