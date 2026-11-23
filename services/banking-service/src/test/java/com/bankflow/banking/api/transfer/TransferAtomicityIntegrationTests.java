package com.bankflow.banking.api.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.application.operation.LedgerWriter;
import com.bankflow.banking.domain.customer.CustomerStatus;
import com.bankflow.banking.domain.ledger.LedgerDirection;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Une défaillance après le débit annule tout : ni solde modifié, ni virement, ni écriture. */
class TransferAtomicityIntegrationTests extends BankingApiTestSupport {

    @Autowired private JdbcTemplate jdbc;

    @MockitoSpyBean private LedgerWriter ledgerWriter;

    @Test
    void uneErreurApresLeDebitNeLaisseAucuneTrace() throws Exception {
        var sourceUser = UUID.randomUUID();
        createCustomerRef(sourceUser, CustomerStatus.ACTIVE);
        var token = createToken(sourceUser, CLIENT);
        var source = parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));
        var destination = parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"));
        var sourceId = source.get("id").asString();
        var destinationId = destination.get("id").asString();
        send("POST", "/accounts/" + sourceId + "/deposits", token, "{\"amount\":\"100.00\"}");
        // Le côté sortant s'écrit, le côté entrant échoue : le débit et le crédit sont déjà faits
        doCallRealMethod()
                .doThrow(new IllegalStateException("panne simulée"))
                .when(ledgerWriter)
                .saveWithAccountEntry(any(), any(LedgerDirection.class));

        var response =
                send(
                        "POST",
                        "/transfers",
                        token,
                        "{\"sourceAccountId\":\""
                                + sourceId
                                + "\",\"destinationIban\":\""
                                + destination.get("accountNumber").asString()
                                + "\",\"amount\":\"60.00\"}",
                        Map.of("Idempotency-Key", "panne"));

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(balanceOf(sourceId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(destinationId)).isEqualByComparingTo("0");
        assertThat(count("transfers", "source_account_id", sourceId)).isZero();
        assertThat(count("transactions", "account_id", sourceId)).isEqualTo(1L);
        assertThat(count("transactions", "account_id", destinationId)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM idempotency_keys WHERE idempotency_key ="
                                        + " 'panne'",
                                Long.class))
                .isZero();
        // Seules les écritures du dépôt initial subsistent
        assertThat(count("ledger_entries", "account_id", sourceId)).isEqualTo(1L);
        assertThat(count("ledger_entries", "account_id", destinationId)).isZero();
        assertLedgerInvariants(jdbc, sourceId, destinationId);
    }

    private long count(String table, String column, String id) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE " + column + " = ?::uuid",
                Long.class,
                id);
    }

    private BigDecimal balanceOf(String accountId) {
        return jdbc.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?::uuid", BigDecimal.class, accountId);
    }
}
