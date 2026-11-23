package com.bankflow.gateway.error;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import java.io.UncheckedIOException;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class GatewayErrorFilterTests {

    private final Logger logger = (Logger) LoggerFactory.getLogger(GatewayErrorFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private Level previousLevel;
    private Instant now = Instant.parse("2026-11-20T10:00:00Z");

    @BeforeEach
    void attachAppender() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    @Test
    void unServiceInjoignableEstJournaliseSansPileEtAuPlusUneFoisParPeriode() throws Exception {
        var filter = new GatewayErrorFilter(createClock());
        FilterChain unavailable =
                (request, response) -> {
                    throw new UncheckedIOException(new ConnectException("Connection refused"));
                };

        for (int attempt = 0; attempt < 5; attempt++) {
            var response = new MockHttpServletResponse();
            filter.doFilter(
                    new MockHttpServletRequest("GET", "/api/customers/me"), response, unavailable);
            assertThat(response.getStatus()).isEqualTo(503);
        }
        now = now.plus(GatewayErrorFilter.UNAVAILABLE_WARN_PERIOD);
        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/customers/me"),
                new MockHttpServletResponse(),
                unavailable);

        assertThat(appender.list).hasSize(6).allMatch(event -> event.getThrowableProxy() == null);
        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.WARN)
                .hasSize(2)
                .allMatch(event -> event.getFormattedMessage().contains("ConnectException"));
    }

    @Test
    void uneErreurInattendueGardeSaPile() throws Exception {
        var filter = new GatewayErrorFilter(createClock());
        FilterChain failing =
                (request, response) -> {
                    throw new IllegalStateException("bogue");
                };

        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/accounts"), response, failing);

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(appender.list)
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getLevel()).isEqualTo(Level.WARN);
                    assertThat(event.getThrowableProxy()).isNotNull();
                });
    }

    private Clock createClock() {
        return new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now;
            }
        };
    }
}
