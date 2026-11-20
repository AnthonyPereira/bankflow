package com.bankflow.gateway.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Traduit les pannes des services internes en réponses {@code ProblemDetail}. */
@Component
@Order(1)
class GatewayErrorFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorFilter.class);

    /** Au plus un WARN par période pour un service injoignable, les suivants passent en DEBUG. */
    static final Duration UNAVAILABLE_WARN_PERIOD = Duration.ofSeconds(30);

    private final Clock clock;
    private final AtomicLong nextUnavailableWarn = new AtomicLong(Long.MIN_VALUE);

    GatewayErrorFilter() {
        this(Clock.systemUTC());
    }

    GatewayErrorFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            if (response.isCommitted()) {
                throw exception;
            }
            var status = resolveStatus(exception);
            logFailure(request, status, exception);
            response.resetBuffer();
            ProblemResponses.write(request, response, status, describeStatus(status));
        }
    }

    /**
     * Un service aval arrêté fait échouer chaque requête de la même façon : sa pile n'apporte rien
     * et remplirait les journaux. Ces cas tiennent en une ligne, au plus une fois par période en
     * WARN. Seule une erreur inattendue garde sa pile complète.
     */
    private void logFailure(HttpServletRequest request, HttpStatus status, Exception exception) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.warn("Échec du relais de {} : {}", request.getRequestURI(), status, exception);
            return;
        }
        var cause = findRootCause(exception);
        if (claimUnavailableWarn()) {
            log.warn(
                    "Échec du relais de {} : {} ({} : {})",
                    request.getRequestURI(),
                    status,
                    cause.getClass().getSimpleName(),
                    cause.getMessage());
        } else {
            log.debug(
                    "Échec du relais de {} : {} ({})",
                    request.getRequestURI(),
                    status,
                    cause.getClass().getSimpleName());
        }
    }

    private boolean claimUnavailableWarn() {
        var now = clock.millis();
        var next = nextUnavailableWarn.get();
        return now >= next
                && nextUnavailableWarn.compareAndSet(
                        next, now + UNAVAILABLE_WARN_PERIOD.toMillis());
    }

    private static Throwable findRootCause(Throwable exception) {
        var cause = exception;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static HttpStatus resolveStatus(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof HttpConnectTimeoutException
                    || cause instanceof ConnectException
                    || cause instanceof UnknownHostException) {
                return HttpStatus.SERVICE_UNAVAILABLE;
            }
            if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static String describeStatus(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "Le service demandé est momentanément indisponible.";
            case GATEWAY_TIMEOUT -> "Le service demandé n'a pas répondu à temps.";
            default -> "Une erreur inattendue est survenue.";
        };
    }
}
