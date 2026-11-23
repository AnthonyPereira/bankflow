package com.bankflow.customer.application.customer;

/** Coordonnées modifiables par le client lui-même. */
public record ContactDetails(String firstName, String lastName, String phone, String address) {}
