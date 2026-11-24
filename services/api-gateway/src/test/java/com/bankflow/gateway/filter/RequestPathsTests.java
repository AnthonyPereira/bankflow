package com.bankflow.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestPathsTests {

    @Test
    void uneUriAbsenteDonneLaRacine() {
        var request =
                new MockHttpServletRequest() {
                    @Override
                    public String getRequestURI() {
                        return null;
                    }
                };

        assertThat(RequestPaths.normalizePath(request)).isEqualTo("/");
    }

    @Test
    void unContextPathAbsentEstIgnore() {
        var request =
                new MockHttpServletRequest("POST", "/api/transfers") {
                    @Override
                    public String getContextPath() {
                        return null;
                    }
                };

        assertThat(RequestPaths.normalizePath(request)).isEqualTo("/api/transfers");
    }

    @Test
    void uneUriHorsDuContextPathResteTelleQuelle() {
        var request = new MockHttpServletRequest("POST", "/api/transfers");
        request.setContextPath("/autre");

        assertThat(RequestPaths.normalizePath(request)).isEqualTo("/api/transfers");
    }

    @Test
    void leContextPathEstRetire() {
        var request = new MockHttpServletRequest("POST", "/app/api//transfers/;x=1");
        request.setContextPath("/app");

        assertThat(RequestPaths.normalizePath(request)).isEqualTo("/api/transfers");
    }
}
