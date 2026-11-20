package com.bankflow.auth.application.token;

public record AccessToken(String value, long expiresInSeconds) {}
