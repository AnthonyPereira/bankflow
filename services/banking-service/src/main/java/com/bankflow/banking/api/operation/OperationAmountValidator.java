package com.bankflow.banking.api.operation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Valide un montant en chaîne décimale ; chaque règle a son message. */
public class OperationAmountValidator implements ConstraintValidator<OperationAmount, String> {

    static final BigDecimal MAX_AMOUNT = new BigDecimal("10000.00");
    private static final Pattern DECIMAL = Pattern.compile("^-?\\d{1,15}(\\.\\d{1,15})?$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return reject(context, "Le montant est obligatoire.");
        }
        if (!DECIMAL.matcher(value).matches()) {
            return reject(context, "Le montant doit être un nombre décimal, par exemple 150.00.");
        }
        var amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            return reject(context, "Le montant doit être positif.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            return reject(context, "Le montant a au plus 2 décimales.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            return reject(context, "Le montant maximal par opération est de 10 000,00.");
        }
        return true;
    }

    /** Montant validé, ramené à 2 décimales (forme canonique utilisée pour l'empreinte). */
    static BigDecimal parseAmount(String value) {
        return new BigDecimal(value).setScale(2, java.math.RoundingMode.UNNECESSARY);
    }

    private static boolean reject(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
