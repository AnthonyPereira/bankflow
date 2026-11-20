package com.bankflow.auth.api.auth.dto;

import com.bankflow.auth.application.token.AccessToken;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

    public static TokenResponse from(AccessToken token) {
        return new TokenResponse(token.value(), "Bearer", token.expiresInSeconds());
    }
}
