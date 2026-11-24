package com.bankflow.gateway.filter;

import com.bankflow.gateway.error.ProblemResponses;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/** Compteurs Bucket4j et réponse 429 communs aux filtres de limitation de débit. */
final class RateLimitBuckets {

    private RateLimitBuckets() {}

    /** Crée un cache borné : une rafale de clés différentes ne peut pas remplir la mémoire. */
    static Cache<String, Bucket> createCache() {
        return Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    static Bucket createBucket(long perMinute) {
        return Bucket.builder()
                .addLimit(limit ->
                        limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }

    /** Répond 429 avec l'en-tête {@code Retry-After}, en secondes. */
    static void writeTooManyRequests(
            HttpServletRequest request, HttpServletResponse response, ConsumptionProbe probe)
            throws IOException {
        var retryAfter = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        ProblemResponses.write(
                request,
                response,
                HttpStatus.TOO_MANY_REQUESTS,
                "Trop de tentatives. Réessayez dans quelques instants.");
    }
}
