package com.bankflow.auth.api.auth.dto;

import com.bankflow.auth.application.token.model.AccessToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse createFrom(AccessToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
