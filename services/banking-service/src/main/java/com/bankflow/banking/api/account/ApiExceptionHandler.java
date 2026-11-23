package com.bankflow.banking.api.account;

import com.bankflow.banking.application.account.AccountLimitReachedException;
import com.bankflow.banking.application.account.AccountNotFoundException;
import com.bankflow.banking.application.account.AccountNumberUnavailableException;
import com.bankflow.banking.application.account.CustomerNotFoundException;
import com.bankflow.banking.application.account.CustomerNotReadyException;
import com.bankflow.banking.application.account.CustomerSuspendedException;
import com.bankflow.banking.application.idempotency.IdempotencyKeyReusedException;
import com.bankflow.banking.application.idempotency.IdempotencyKeys;
import com.bankflow.banking.application.idempotency.InvalidIdempotencyKeyException;
import com.bankflow.banking.application.transaction.InvalidPeriodException;
import com.bankflow.banking.application.transaction.TransactionNotFoundException;
import com.bankflow.banking.application.transfer.DestinationAccountNotFoundException;
import com.bankflow.banking.application.transfer.SameAccountTransferException;
import com.bankflow.banking.application.transfer.TransferCurrencyMismatchException;
import com.bankflow.banking.application.transfer.TransferNotFoundException;
import com.bankflow.banking.domain.account.AccountBalanceNotZeroException;
import com.bankflow.banking.domain.account.AccountClosedException;
import com.bankflow.banking.domain.account.InsufficientFundsException;
import com.bankflow.banking.domain.transaction.AlreadyCompensatedException;
import com.bankflow.banking.domain.transaction.CompensationNotAllowedException;
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

/** Traduit les erreurs de l'API au format {@code ProblemDetail} (RFC 9457) avec un {@code code}. */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String INVALID_DATA = "Les données envoyées sont invalides.";

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleAccountNotFound(AccountNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", exception);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    ProblemDetail handleCustomerNotFound(CustomerNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", exception);
    }

    @ExceptionHandler(FieldValidationException.class)
    ResponseEntity<Object> handleFieldValidation(
            FieldValidationException exception, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        errors.put(exception.getField(), exception.getMessage());
        return buildValidationResponse(
                exception, errors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
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

    @ExceptionHandler(AccountNumberUnavailableException.class)
    ProblemDetail handleAccountNumberUnavailable(AccountNumberUnavailableException exception) {
        return buildProblem(
                HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_NUMBER_UNAVAILABLE", exception);
    }

    @ExceptionHandler(InsufficientFundsException.class)
    ProblemDetail handleInsufficientFunds(InsufficientFundsException exception) {
        return buildProblem(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", exception);
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail handleTransactionNotFound(TransactionNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", exception);
    }

    @ExceptionHandler(DestinationAccountNotFoundException.class)
    ProblemDetail handleDestinationAccountNotFound(DestinationAccountNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "DESTINATION_ACCOUNT_NOT_FOUND", exception);
    }

    @ExceptionHandler(TransferNotFoundException.class)
    ProblemDetail handleTransferNotFound(TransferNotFoundException exception) {
        return buildProblem(HttpStatus.NOT_FOUND, "TRANSFER_NOT_FOUND", exception);
    }

    @ExceptionHandler(TransferCurrencyMismatchException.class)
    ProblemDetail handleTransferCurrencyMismatch(TransferCurrencyMismatchException exception) {
        return buildProblem(HttpStatus.CONFLICT, "CURRENCY_MISMATCH", exception);
    }

    @ExceptionHandler(SameAccountTransferException.class)
    ResponseEntity<Object> handleSameAccountTransfer(
            SameAccountTransferException exception, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        errors.put("destinationIban", exception.getMessage());
        return buildValidationResponse(
                exception, errors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(CompensationNotAllowedException.class)
    ProblemDetail handleCompensationNotAllowed(CompensationNotAllowedException exception) {
        return buildProblem(HttpStatus.CONFLICT, "COMPENSATION_NOT_ALLOWED", exception);
    }

    @ExceptionHandler(AlreadyCompensatedException.class)
    ProblemDetail handleAlreadyCompensated(AlreadyCompensatedException exception) {
        return buildProblem(HttpStatus.CONFLICT, "ALREADY_COMPENSATED", exception);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    ProblemDetail handleIdempotencyKeyReused(IdempotencyKeyReusedException exception) {
        return buildProblem(HttpStatus.UNPROCESSABLE_CONTENT, "IDEMPOTENCY_KEY_REUSED", exception);
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    ResponseEntity<Object> handleInvalidIdempotencyKey(
            InvalidIdempotencyKeyException exception, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        errors.put(IdempotencyKeys.HEADER, exception.getMessage());
        return buildValidationResponse(
                exception, errors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(InvalidPeriodException.class)
    ResponseEntity<Object> handleInvalidPeriod(
            InvalidPeriodException exception, WebRequest request) {
        var errors = new LinkedHashMap<String, String>();
        errors.put("from", exception.getMessage());
        return buildValidationResponse(
                exception, errors, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleConcurrentModification() {
        var problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "La ressource a été modifiée en même temps, réessayez.");
        problem.setProperty("code", "CONCURRENT_MODIFICATION");
        return problem;
    }

    /** Dernier filet : jamais de message d'exception ni de pile dans la réponse. */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) throws Exception {
        if (exception instanceof AccessDeniedException
                || exception instanceof AuthenticationException) {
            // Laissées à Spring Security, qui produit le 401 ou le 403
            throw exception;
        }
        log.error("Erreur inattendue", exception);
        var problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Une erreur interne est survenue, réessayez plus tard.");
        problem.setProperty("code", "INTERNAL_ERROR");
        return problem;
    }

    /** Ajoute un {@code code} (et un détail français) aux erreurs produites par Spring MVC. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        // Le corps n'est fabriqué par Spring que dans le parent : on complète le résultat
        var response = super.handleExceptionInternal(exception, body, headers, status, request);
        if (response != null
                && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null
                        || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", resolveGenericCode(status));
            problem.setDetail(resolveGenericDetail(status, problem.getDetail()));
        }
        return response;
    }

    private static String resolveGenericCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> "VALIDATION_ERROR";
            case 404 -> "RESOURCE_NOT_FOUND";
            case 405, 406, 415 -> "REQUEST_NOT_SUPPORTED";
            default -> status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_REJECTED";
        };
    }

    private static String resolveGenericDetail(HttpStatusCode status, String current) {
        return switch (status.value()) {
            case 400 -> INVALID_DATA;
            case 404 -> "Ressource introuvable.";
            case 405, 406, 415 -> "Cette requête n'est pas prise en charge.";
            default -> status.is5xxServerError() ? "Une erreur interne est survenue." : current;
        };
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
