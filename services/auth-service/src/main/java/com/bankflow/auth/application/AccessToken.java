package com.bankflow.auth.application;

public record AccessToken(String value, long expiresInSeconds) {}
