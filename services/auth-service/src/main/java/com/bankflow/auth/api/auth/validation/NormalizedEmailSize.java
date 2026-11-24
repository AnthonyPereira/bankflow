package com.bankflow.auth.api.auth.validation;

import com.bankflow.auth.domain.user.User;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Limite la longueur d'une adresse telle qu'elle sera enregistrée, après {@link
 * User#normalizeEmail}. La mise en minuscules peut allonger une adresse : « İ » devient « i̇ »,
 * soit deux caractères.
 */
@Documented
@Constraint(validatedBy = NormalizedEmailSize.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface NormalizedEmailSize {

    int max();

    String message();

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    /** Une adresse absente est laissée à {@code @NotBlank}. */
    class Validator implements ConstraintValidator<NormalizedEmailSize, String> {

        private int max;

        @Override
        public void initialize(NormalizedEmailSize constraint) {
            this.max = constraint.max();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || User.normalizeEmail(value).length() <= max;
        }
    }
}
