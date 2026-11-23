package com.bankflow.customer.api.customer;

import com.bankflow.customer.application.customer.CustomerNotFoundException;
import com.bankflow.customer.domain.customer.DemoCustomerCannotBeSuspendedException;
import java.util.LinkedHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
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

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail handleCustomerNotFound(CustomerNotFoundException exception) {
        return createProblem(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(DemoCustomerCannotBeSuspendedException.class)
    ProblemDetail handleDemoCustomerCannotBeSuspended(
            DemoCustomerCannotBeSuspendedException exception) {
        return createProblem(
                HttpStatus.CONFLICT, "DEMO_CUSTOMER_CANNOT_BE_SUSPENDED", exception.getMessage());
    }

    /** Deux modifications simultanées du même client : la seconde doit être rejouée. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification(ObjectOptimisticLockingFailureException exception) {
        return createProblem(
                HttpStatus.CONFLICT,
                "CONCURRENT_MODIFICATION",
                "Le client a été modifié en même temps, veuillez réessayer.");
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
        return handleExceptionInternal(
                exception, createValidationProblem(status, errors), headers, status, request);
    }

    /** Paramètres de requête invalides (page, size, q). */
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
        return handleExceptionInternal(
                exception, createValidationProblem(status, errors), headers, status, request);
    }

    private static ProblemDetail createProblem(HttpStatus status, String code, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("code", code);
        return problem;
    }

    private static ProblemDetail createValidationProblem(
            HttpStatusCode status, LinkedHashMap<String, String> errors) {
        var problem = ProblemDetail.forStatusAndDetail(status, INVALID_DATA);
        problem.setProperty("errors", errors);
        return problem;
    }
}
