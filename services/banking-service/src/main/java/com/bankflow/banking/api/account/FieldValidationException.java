package com.bankflow.banking.api.account;

/** Une règle de validation qui dépend du rôle de l'appelant, donc hors des annotations du DTO. */
class FieldValidationException extends RuntimeException {

    private final String field;

    FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    String getField() {
        return field;
    }
}
