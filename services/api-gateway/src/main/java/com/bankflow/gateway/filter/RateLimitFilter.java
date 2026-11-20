package com.bankflow.gateway.filter;

import com.bankflow.gateway.error.ProblemResponses;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limite par adresse IP le débit des routes sensibles. Les compteurs restent en mémoire, ce qui
 * suffit pour une instance unique de la Gateway (ADR-001, section 2.3).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, Long> limitsPerMinute;

    // Le cache borné évite qu'une rafale d'adresses différentes remplisse la mémoire
    private final Cache<String, Bucket> buckets =
            Caffeine.newBuilder()
                    .maximumSize(100_000)
                    .expireAfterAccess(Duration.ofMinutes(10))
                    .build();

    RateLimitFilter(
            @Value("${bankflow.rate-limit.login-per-minute}") long login,
            @Value("${bankflow.rate-limit.register-per-minute}") long register,
            @Value("${bankflow.rate-limit.transfers-per-minute}") long transfers) {
        this.limitsPerMinute =
                Map.of(
                        "/api/auth/login", login,
                        "/api/auth/register", register,
                        "/api/transfers", transfers);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod())
                || !limitsPerMinute.containsKey(normalizedPath(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var path = normalizedPath(request);
        var bucket =
                buckets.get(
                        path + '|' + request.getRemoteAddr(),
                        key -> newBucket(limitsPerMinute.get(path)));
        var probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            chain.doFilter(request, response);
            return;
        }
        var retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        ProblemResponses.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives. Réessayez dans quelques instants.");
    }

    private static Bucket newBucket(long perMinute) {
        return Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }

    private static String normalizedPath(HttpServletRequest request) {
        var path = request.getRequestURI();
        return path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1)
                : path;
    }
}
