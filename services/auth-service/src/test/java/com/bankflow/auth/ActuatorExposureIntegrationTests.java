package com.bankflow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.web.PathMappedEndpoints;

/** Seuls health et info sont exposés en HTTP : prometheus, protégé par JWT, est retiré en 0.1.0. */
class ActuatorExposureIntegrationTests extends AuthApiTestSupport {

    @Autowired private PathMappedEndpoints endpoints;

    @Test
    void seulsHealthEtInfoSontExposes() throws Exception {
        assertThat(endpoints.getPath(EndpointId.of("health"))).isNotNull();
        assertThat(endpoints.getPath(EndpointId.of("info"))).isNotNull();
        assertThat(endpoints.getPath(EndpointId.of("prometheus"))).isNull();
        assertThat(send(buildRequest("/actuator/health")).statusCode()).isEqualTo(200);
    }
}
