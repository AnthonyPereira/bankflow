package com.bankflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class UserRateLimitFilterTests {

    private final UserRateLimitFilter filter = new UserRateLimitFilter(1);

    @AfterEach
    void viderLeContexte() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sansAuthentificationLaRequeteContinueSansCompteur() throws Exception {
        for (int i = 0; i < 3; i++) {
            var chain = executer();
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void uneAuthentificationNonJwtLaisseContinuer() throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("alice", "x"));

        assertThat(executer().getRequest()).isNotNull();
        assertThat(executer().getRequest()).isNotNull();
    }

    @Test
    void unJwtSansSujetLaisseContinuer() throws Exception {
        var jwt =
                new Jwt(
                        "t",
                        Instant.now(),
                        Instant.now().plusSeconds(60),
                        Map.of("alg", "none"),
                        Map.of("scope", "x"));
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(executer().getRequest()).isNotNull();
        assertThat(executer().getRequest()).isNotNull();
    }

    private MockFilterChain executer() throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/transfers");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        return chain;
    }
}
