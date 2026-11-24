package com.bankflow.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite par utilisateur le débit des virements. La limite par adresse IP ({@link RateLimitFilter})
 * reste en place, mais un visiteur authentifié pourrait la contourner en changeant d'IP pour sonder
 * des IBAN, à partir des réponses {@code DESTINATION_ACCOUNT_NOT_FOUND} ou {@code
 * CURRENCY_MISMATCH}. Ce filtre s'exécute après la chaîne Spring Security, car il lit le sujet du
 * JWT validé. Les compteurs restent en mémoire, ce qui suffit pour une instance unique de la
 * Gateway (ADR-001, section 2.3).
 */
@Component
@Order(1)
class UserRateLimitFilter extends OncePerRequestFilter {

    private static final String TRANSFERS_PATH = "/api/transfers";

    private final long transfersPerMinute;
    private final Cache<String, Bucket> buckets = RateLimitBuckets.createCache();

    UserRateLimitFilter(
            @Value("${bankflow.rate-limit.transfers-per-user-per-minute}")
                    long transfersPerMinute) {
        this.transfersPerMinute = transfersPerMinute;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())
                || !TRANSFERS_PATH.equals(RequestPaths.normalizePath(request))) {
            chain.doFilter(request, response);
            return;
        }
        // Sans jeton validé, la sécurité a déjà répondu 401. Un jeton sans sujet passe sans être
        // compté : la Gateway n'envoie alors pas X-User-Id et le Banking Service refuse la requête.
        var subject =
                SecurityContextHolder.getContext().getAuthentication()
                                instanceof JwtAuthenticationToken token
                        ? token.getToken().getSubject()
                        : null;
        if (subject == null) {
            chain.doFilter(request, response);
            return;
        }
        var bucket =
                buckets.get(
                        "transfers|" + subject,
                        key -> RateLimitBuckets.createBucket(transfersPerMinute));
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitBuckets.writeTooManyRequests(request, response, probe);
    }
}
