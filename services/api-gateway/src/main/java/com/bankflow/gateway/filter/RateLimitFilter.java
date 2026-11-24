package com.bankflow.gateway.filter;

import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Limite par adresse IP le débit des routes sensibles (par préfixe /64 en IPv6, voir {@link
 * ClientAddressKeys}). Les compteurs restent en mémoire, ce qui suffit pour une instance unique de
 * la Gateway (ADR-001, section 2.3).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter extends OncePerRequestFilter {

    // Toutes les opérations bancaires d'une même adresse partagent un seul compteur
    private static final String OPERATIONS_BUCKET = "operations";
    private static final List<PathPattern> OPERATION_PATTERNS =
            Stream.of(
                            "/api/accounts",
                            "/api/accounts/*/deposits",
                            "/api/accounts/*/withdrawals",
                            "/api/accounts/*/close",
                            "/api/transactions/*/compensations")
                    .map(PathPatternParser.defaultInstance::parse)
                    .toList();

    private final Map<String, Long> limitsPerMinute;
    private final long operationsPerMinute;

    private final Cache<String, Bucket> buckets = RateLimitBuckets.createCache();

    RateLimitFilter(
            @Value("${bankflow.rate-limit.login-per-minute}") long login,
            @Value("${bankflow.rate-limit.register-per-minute}") long register,
            @Value("${bankflow.rate-limit.transfers-per-minute}") long transfers,
            @Value("${bankflow.rate-limit.operations-per-minute}") long operations) {
        this.operationsPerMinute = operations;
        this.limitsPerMinute =
                Map.of(
                        "/api/auth/login", login,
                        "/api/auth/register", register,
                        "/api/transfers", transfers);
    }

    // Le tri se fait ici plutôt que dans shouldNotFilter : le chemin n'est normalisé qu'une fois
    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        var path = RequestPaths.normalizePath(request);
        var bucketName = path;
        var perMinute = limitsPerMinute.get(path);
        if (perMinute == null && isOperation(path)) {
            bucketName = OPERATIONS_BUCKET;
            perMinute = operationsPerMinute;
        }
        if (perMinute == null) {
            chain.doFilter(request, response);
            return;
        }
        var limit = perMinute;
        var client = ClientAddressKeys.resolveKey(request.getRemoteAddr());
        var bucket =
                buckets.get(bucketName + '|' + client, key -> RateLimitBuckets.createBucket(limit));
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        RateLimitBuckets.writeTooManyRequests(request, response, probe);
    }

    private static boolean isOperation(String path) {
        var container = PathContainer.parsePath(path);
        return OPERATION_PATTERNS.stream().anyMatch(pattern -> pattern.matches(container));
    }
}
