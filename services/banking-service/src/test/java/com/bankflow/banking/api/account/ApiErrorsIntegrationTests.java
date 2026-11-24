package com.bankflow.banking.api.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.banking.BankingApiTestSupport;
import com.bankflow.banking.domain.customer.CustomerStatus;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Toute erreur HTTP porte un {@code code}, y compris celles produites par Spring. */
@Import(ApiErrorsIntegrationTests.FailingController.class)
class ApiErrorsIntegrationTests extends BankingApiTestSupport {

    /** Route de test qui lève une exception imprévue contenant une information sensible. */
    @RestController
    static class FailingController {

        @GetMapping("/test/boom")
        String failUnexpectedly() {
            throw new IllegalStateException("secret-interne");
        }
    }

    private String createClientToken() {
        var userId = UUID.randomUUID();
        createCustomerRef(userId, CustomerStatus.ACTIVE);
        return createToken(userId, CLIENT);
    }

    private void assertProblem(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow())
                .contains("application/problem+json");
        assertThat(parseJson(response).get("code").asString()).isEqualTo(code);
    }

    @Test
    void unCorpsIllisibleDonneValidationError() throws Exception {
        var response = send("POST", "/accounts", createClientToken(), "{pas du json");

        assertProblem(response, 400, "VALIDATION_ERROR");
    }

    @Test
    void uneDeviseInconnueDonneValidationError() throws Exception {
        var response = send("POST", "/accounts", createClientToken(), "{\"currency\":\"GBP\"}");

        assertProblem(response, 400, "VALIDATION_ERROR");
    }

    @Test
    void unUuidInvalideDansLeCheminDonneValidationError() throws Exception {
        var response = send("GET", "/accounts/pas-un-uuid", createClientToken(), null);

        assertProblem(response, 400, "VALIDATION_ERROR");
    }

    @Test
    void unePageAbusiveDonneValidationErrorSurLesComptesEtLesOperations() throws Exception {
        var token = createClientToken();

        assertProblem(
                send("GET", "/accounts?page=100000000", token, null), 400, "VALIDATION_ERROR");
        assertProblem(
                send("GET", "/transactions?page=100000000", token, null), 400, "VALIDATION_ERROR");
        assertThat(send("GET", "/accounts?page=10000", token, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/transactions?page=10001", token, null).statusCode())
                .isEqualTo(400);
    }

    @Test
    void unParametreDePageNonNumeriqueDonneValidationError() throws Exception {
        var response = send("GET", "/accounts?page=abc", createClientToken(), null);

        assertProblem(response, 400, "VALIDATION_ERROR");
    }

    @Test
    void uneTailleHorsLimitesDonneValidationErrorEtLeDetailDesChamps() throws Exception {
        var response = send("GET", "/accounts?size=101", createClientToken(), null);

        assertProblem(response, 400, "VALIDATION_ERROR");
        assertThat(parseJson(response).get("errors").get("size").asString()).isNotBlank();
    }

    @Test
    void uneMethodeNonSupporteeDonneRequestNotSupported() throws Exception {
        var response = send("DELETE", "/accounts", createClientToken(), null);

        assertProblem(response, 405, "REQUEST_NOT_SUPPORTED");
    }

    @Test
    void unTypeDeContenuNonSupporteDonneRequestNotSupported() throws Exception {
        var response =
                send(
                        "POST",
                        "/accounts",
                        createClientToken(),
                        "currency=EUR",
                        Map.of("Content-Type", "text/plain"));

        assertProblem(response, 415, "REQUEST_NOT_SUPPORTED");
    }

    @Test
    void uneExceptionImprevueDonneInternalErrorSansFuiteDeMessage() throws Exception {
        var response = send("GET", "/test/boom", createClientToken(), null);

        assertProblem(response, 500, "INTERNAL_ERROR");
        assertThat(response.body()).doesNotContain("secret-interne").doesNotContain("Exception");
    }
}
