package com.bankflow.gateway.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Garantit un identifiant de corrélation valide sur chaque requête et chaque réponse. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Correlation-Id";
    static final String MDC_KEY = "correlationId";

    // Une valeur libre pourrait injecter de fausses lignes dans les logs
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var received = request.getHeader(HEADER);
        var correlationId =
                received != null && VALID_ID.matcher(received).matches()
                        ? received
                        : UUID.randomUUID().toString();

        var wrapped = new MutableHeadersRequest(request);
        wrapped.set(HEADER, correlationId);
        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        try {
            chain.doFilter(wrapped, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
