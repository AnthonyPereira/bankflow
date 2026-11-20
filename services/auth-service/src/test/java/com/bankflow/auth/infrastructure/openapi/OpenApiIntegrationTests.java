package com.bankflow.auth.infrastructure.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.AuthApiTestSupport;
import org.junit.jupiter.api.Test;

class OpenApiIntegrationTests extends AuthApiTestSupport {

    @Test
    void laDocumentationDecritLesRoutesDAuthentification() throws Exception {
        var response = send(request("/v3/api-docs"));

        assertThat(response.statusCode()).isEqualTo(200);
        var paths = json(response).get("paths");
        assertThat(paths.has("/auth/register")).isTrue();
        assertThat(paths.has("/auth/login")).isTrue();
        assertThat(paths.has("/auth/refresh")).isTrue();
        assertThat(paths.has("/auth/logout")).isTrue();
        assertThat(paths.has("/auth/me")).isTrue();
    }

    @Test
    void swaggerUiEstDisponible() throws Exception {
        assertThat(send(request("/swagger-ui/index.html")).statusCode()).isEqualTo(200);
    }
}
