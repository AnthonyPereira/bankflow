package com.bankflow.banking.api.operation.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Montant d'une opération : positif, 2 décimales au plus, 10 000,00 au plus. */
@Documented
@Constraint(validatedBy = OperationAmountValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAmount {

    String message() default "Le montant est invalide.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
