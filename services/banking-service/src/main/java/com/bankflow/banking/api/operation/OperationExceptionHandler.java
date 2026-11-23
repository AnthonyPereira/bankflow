package com.bankflow.banking.api.operation;

import com.bankflow.banking.application.idempotency.IdempotencyKeyReusedException;
import com.bankflow.banking.application.idempotency.IdempotencyKeys;
import com.bankflow.banking.application.idempotency.InvalidIdempotencyKeyException;
import com.bankflow.banking.application.transaction.InvalidPeriodException;
import com.bankflow.banking.application.transaction.TransactionNotFoundException;
import com.bankflow.banking.domain.account.InsufficientFundsException;
import com.bankflow.banking.domain.transaction.AlreadyCompensatedException;
import com.bankflow.banking.domain.transaction.CompensationNotAllowedException;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Erreurs des opérations, des opérations compensatrices et de l'idempotence. Passe avant le
 * gestionnaire général, dont le filet {@code Exception} intercepterait tout.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class OperationExceptionHandler {

    private static final String INVALID_DATA = "Les données envoyées sont invalides.";

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException exception) {
        return buildProblem(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail handleTransactionNotFound(TransactionNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(CompensationNotAllowedException.class)
    ProblemDetail handleCompensationNotAllowed(CompensationNotAllowedException exception) {
        return buildProblem(
                HttpStatus.CONFLICT, "COMPENSATION_NOT_ALLOWED", exception.getMessage());
    }

    @ExceptionHandler(AlreadyCompensatedException.class)
    ProblemDetail handleAlreadyCompensated(AlreadyCompensatedException exception) {
        return buildProblem(HttpStatus.CONFLICT, "ALREADY_COMPENSATED", exception.getMessage());
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return buildProblem(
                HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", exception.getMessage());
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ProblemDetail handleInvalidIdempotencyKey(InvalidIdempotencyKeyException exception) {
        return buildValidationProblem(Map.of(IdempotencyKeys.HEADER, exception.getMessage()));
    }

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail handleInvalidPeriod(InvalidPeriodException exception) {
        return buildValidationProblem(Map.of("from", exception.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        var errors = new LinkedHashMap<String, String>();
        exception
                .getConstraintViolations()
                .forEach(violation ->
                        errors.putIfAbsent(
                                violation.getPropertyPath().toString(), violation.getMessage()));
        return buildValidationProblem(errors);
    }

    private static ProblemDetail buildValidationProblem(Map<String, String> errors) {
        var problem = buildProblem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", INVALID_DATA);
        problem.setProperty("errors", errors);
        return problem;
    }

    private static ProblemDetail buildProblem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }
}
