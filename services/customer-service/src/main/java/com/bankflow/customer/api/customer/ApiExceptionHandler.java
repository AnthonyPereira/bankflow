package com.bankflow.customer.api.customer;

import com.bankflow.customer.application.customer.CustomerNotFoundException;
import com.bankflow.customer.domain.customer.DemoCustomerCannotBeSuspendedException;
import java.util.LinkedHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit les erreurs de l'API au format {@code ProblemDetail} (RFC 9457). Toutes les réponses
 * d'erreur portent un {@code code}, y compris celles que produit Spring.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String INVALID_DATA = "Les données envoyées sont invalides.";
    private static final String FALLBACK_PARAMETER_NAME = "parametre";

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
                "La ressource a été modifiée en même temps, réessayez.");
    }

    @ExceptionHandler(InvalidTokenException.class)
    ResponseEntity<ProblemDetail> handleInvalidToken(InvalidTokenException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(
                        createProblem(
                                HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", exception.getMessage()));
    }

    /**
     * Filet de sécurité : rien de l'exception (message, pile) ne part dans la réponse. Les refus
     * d'accès sont relancés pour que Spring Security produise ses propres 401 et 403.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) throws Exception {
        if (exception instanceof AccessDeniedException
                || exception instanceof AuthenticationException) {
            throw exception;
        }
        log.error("Erreur inattendue", exception);
        return createProblem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "Une erreur inattendue est survenue.");
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
                .forEach(result -> {
                    var name = result.getMethodParameter().getParameterName();
                    result.getResolvableErrors()
                            .forEach(error ->
                                    errors.putIfAbsent(
                                            name != null ? name : FALLBACK_PARAMETER_NAME,
                                            String.valueOf(error.getDefaultMessage())));
                });
        return handleExceptionInternal(
                exception, createValidationProblem(status, errors), headers, status, request);
    }

    /** Point de passage des erreurs de Spring (corps illisible, UUID invalide, 405, 415…). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        var response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            addCodeIfMissing(problem, statusCode);
        }
        return response;
    }

    private static void addCodeIfMissing(ProblemDetail problem, HttpStatusCode status) {
        if (problem.getProperties() != null && problem.getProperties().containsKey("code")) {
            return;
        }
        var code = resolveCode(status);
        problem.setProperty("code", code);
        // Les messages de Spring sont en anglais : on les remplace par un texte français.
        if ("VALIDATION_ERROR".equals(code)) {
            problem.setDetail(INVALID_DATA);
        } else if ("INTERNAL_ERROR".equals(code)) {
            problem.setDetail("Une erreur inattendue est survenue.");
        }
    }

    private static String resolveCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405, 406, 415 -> "REQUEST_NOT_SUPPORTED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
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
