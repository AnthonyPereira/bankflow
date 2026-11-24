package com.bankflow.customer.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.customer.CustomerApiTestSupport;
import org.junit.jupiter.api.Test;

class OpenApiIntegrationTests extends CustomerApiTestSupport {

    @Test
    void laDocumentationDecritLesRoutesDesClients() throws Exception {
        var response = get("/v3/api-docs", null);

        assertThat(response.statusCode()).isEqualTo(200);
        var paths = parseJson(response).get("paths");
        assertThat(paths.has("/customers/me")).isTrue();
        assertThat(paths.has("/customers/{id}")).isTrue();
        assertThat(paths.has("/customers")).isTrue();
        assertThat(paths.has("/customers/{id}/status")).isTrue();
    }

    @Test
    void swaggerUiEstDisponible() throws Exception {
        assertThat(get("/swagger-ui/index.html", null).statusCode()).isEqualTo(200);
    }
}
