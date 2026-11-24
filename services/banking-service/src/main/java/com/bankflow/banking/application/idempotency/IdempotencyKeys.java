package com.bankflow.banking.application.idempotency;

import com.bankflow.banking.application.idempotency.exception.InvalidIdempotencyKeyException;
import java.util.regex.Pattern;

/** Règles de l'en-tête {@code Idempotency-Key}. */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

    private IdempotencyKeys() {}

    /**
     * Vérifie la clé. Une clé absente est acceptée sauf si {@code required} : elle est alors
     * obligatoire (virements).
     */
    public static void validate(String key, boolean required) {
        if (key == null) {
            if (required) {
                throw new InvalidIdempotencyKeyException("La clé d'idempotence est obligatoire.");
            }
            return;
        }
        if (!FORMAT.matcher(key).matches()) {
            throw new InvalidIdempotencyKeyException(
                    "La clé doit contenir 1 à 64 caractères : lettres, chiffres, tiret ou"
                            + " souligné.");
        }
    }
}
