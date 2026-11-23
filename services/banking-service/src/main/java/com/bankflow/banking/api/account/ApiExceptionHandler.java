package com.bankflow.banking.api.account;

import com.bankflow.banking.application.account.AccountLimitReachedException;
import com.bankflow.banking.application.account.AccountNotFoundException;
import com.bankflow.banking.application.account.CustomerNotReadyException;
import com.bankflow.banking.application.account.CustomerSuspendedException;
import com.bankflow.banking.domain.account.AccountBalanceNotZeroException;
import com.bankflow.banking.domain.account.AccountClosedException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduit les erreurs de l'API au format {@code ProblemDetail} (RFC 9457) avec un {@code code}. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String INVALID_DATA = "Les données envoyées sont invalides.";

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", exception);
    }

    @ExceptionHandler(CustomerNotReadyException.class)
    ProblemDetail handleCustomerNotReady(CustomerNotReadyException exception) {
        return buildProblem(HttpStatus.CONFLICT, "CUSTOMER_NOT_READY", exception);
    }

    @ExceptionHandler(CustomerSuspendedException.class)
    ProblemDetail handleCustomerSuspended(CustomerSuspendedException exception) {
        return buildProblem(HttpStatus.FORBIDDEN, "CUSTOMER_SUSPENDED", exception);
    }

    @ExceptionHandler(AccountLimitReachedException.class)
    ProblemDetail handleAccountLimitReached(AccountLimitReachedException exception) {
        return buildProblem(HttpStatus.CONFLICT, "ACCOUNT_LIMIT_REACHED", exception);
    }

    @ExceptionHandler(AccountClosedException.class)
    ProblemDetail handleAccountClosed(AccountClosedException exception) {
        return buildProblem(HttpStatus.CONFLICT, "ACCOUNT_CLOSED", exception);
    }

    @ExceptionHandler(AccountBalanceNotZeroException.class)
    ProblemDetail handleAccountBalanceNotZero(AccountBalanceNotZeroException exception) {
        return buildProblem(HttpStatus.CONFLICT, "ACCOUNT_BALANCE_NOT_ZERO", exception);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        exception
                .getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return buildValidationResponse(exception, errors, headers, status, request);
    }

    /** Paramètres de requête invalides (page, size) : validés par Spring sur les paramètres. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        exception
                .getParameterValidationResults()
                .forEach(result ->
                        result.getResolvableErrors()
                                .forEach(error ->
                                        errors.putIfAbsent(
                                                String.valueOf(
                                                        result.getMethodParameter()
                                                                .getParameterName()),
                                                String.valueOf(error.getDefaultMessage()))));
        return buildValidationResponse(exception, errors, headers, status, request);
    }

    private ResponseEntity<Object> buildValidationResponse(
            Exception exception,
            LinkedHashMap<String, String> errors,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var problem = ProblemDetail.forStatusAndDetail(status, INVALID_DATA);
        problem.setProperty("code", "VALIDATION_ERROR");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static ProblemDetail buildProblem(
            HttpStatus status, String code, RuntimeException exception) {
        var problem = ProblemDetail.forStatusAndDetail(status, exception.getMessage());
        problem.setProperty("code", code);
        return problem;
    }
}
