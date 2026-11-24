package com.bankflow.auth.application.token.model;

public record AccessToken(String value, long expiresInSeconds) {}
