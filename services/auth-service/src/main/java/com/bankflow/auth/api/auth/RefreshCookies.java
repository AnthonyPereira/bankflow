package com.bankflow.auth.api.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Construit le cookie du refresh token décrit dans l'ADR-003. */
@Component
class RefreshCookies {

    static final String NAME = "bankflow_refresh";

    private final boolean secure;

    RefreshCookies(@Value("${bankflow.refresh.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    String create(String value, Duration timeToLive) {
        return base(value).maxAge(timeToLive).build().toString();
    }

    String clear() {
        return base("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/auth");
    }
}
