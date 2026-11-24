package com.bankflow.banking.api.error;

/** Une règle de validation qui dépend du rôle de l'appelant, donc hors des annotations du DTO. */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    String getField() {
        return field;
    }
}
