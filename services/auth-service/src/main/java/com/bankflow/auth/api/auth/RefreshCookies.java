package com.bankflow.auth.api.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** Construit le cookie du refresh token décrit dans l'ADR-003. */
@Component
public class RefreshCookies {

    public static final String NAME = "bankflow_refresh";

    private final boolean secure;

    public RefreshCookies(@Value("${bankflow.refresh.cookie-secure}") boolean secure) {
        this.secure = secure;
    }

    public String create(String value, Duration timeToLive) {
        return buildBaseCookie(value).maxAge(timeToLive).build().toString();
    }

    public String clear() {
        return buildBaseCookie("").maxAge(Duration.ZERO).build().toString();
    }

    private ResponseCookie.ResponseCookieBuilder buildBaseCookie(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/api/auth");
    }
}
