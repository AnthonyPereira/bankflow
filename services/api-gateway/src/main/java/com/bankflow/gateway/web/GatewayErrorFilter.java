package com.bankflow.gateway.web;

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
            var status = statusOf(exception);
            log.warn("Échec du relais de {} : {}", request.getRequestURI(), status, exception);
            response.resetBuffer();
            ProblemResponses.write(request, response, status, detailOf(status));
        }
    }

    private static HttpStatus statusOf(Throwable exception) {
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

    private static String detailOf(HttpStatus status) {
        return switch (status) {
            case SERVICE_UNAVAILABLE -> "Le service demandé est momentanément indisponible.";
            case GATEWAY_TIMEOUT -> "Le service demandé n'a pas répondu à temps.";
            default -> "Une erreur inattendue est survenue.";
        };
    }
}
