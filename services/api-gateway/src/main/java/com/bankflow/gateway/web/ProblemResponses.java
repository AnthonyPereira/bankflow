package com.bankflow.gateway.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

/** Écrit les erreurs de la Gateway au format {@code ProblemDetail} (RFC 9457). */
public final class ProblemResponses {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ProblemResponses() {}

    public static void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String detail)
            throws IOException {
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());
        body.put("correlationId", MDC.get(CorrelationIdFilter.MDC_KEY));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8);
        response.getWriter().write(JSON.writeValueAsString(body));
    }
}
