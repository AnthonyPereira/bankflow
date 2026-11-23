package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.account.AccountNumberGenerator;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Une collision de numéro est absorbée par la base : nouvel essai, jamais de 500. */
class AccountNumberCollisionIntegrationTests extends BankingApiTestSupport {

    @MockitoSpyBean private AccountNumberGenerator numberGenerator;

    private String createTakenNumber() throws Exception {
        var otherUser = UUID.randomUUID();
        createCustomerRef(otherUser, CustomerStatus.ACTIVE);
        var token = createToken(otherUser, CLIENT);
        return parseJson(send("POST", "/accounts", token, "{\"currency\":\"EUR\"}"))
                .get("accountNumber")
                .asString();
    }

    @Test
    void unNumeroDejaPrisEstRemplaceParUnNumeroLibre() throws Exception {
        var taken = createTakenNumber();
        var free = "FR7699999000010000000000197";
        doReturn(taken, free).when(numberGenerator).generateIban();
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);

        var response =
                send("POST", "/accounts", createToken(userId, CLIENT), "{\"currency\":\"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(parseJson(response).get("accountNumber").asString()).isEqualTo(free);
    }

    @Test
    void troisNumerosPrisDonnentUn503AccountNumberUnavailable() throws Exception {
        var taken = createTakenNumber();
        doReturn(taken, taken, taken).when(numberGenerator).generateIban();
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        var token = createToken(userId, CLIENT);

        var response = send("POST", "/accounts", token, "{\"currency\":\"EUR\"}");

        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(parseJson(response).get("code").asString())
                .isEqualTo("ACCOUNT_NUMBER_UNAVAILABLE");
        assertThat(parseJson(send("GET", "/accounts", token, null)).get("totalElements").asInt())
                .isZero();
    }
}
