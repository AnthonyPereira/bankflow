package com.bankflow.customer.api.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bankflow.customer.domain.customer.Customer;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unVerrouOptimisteDonne409ConcurrentModification() {
        var problem =
                handler.handleConcurrentModification(
                        new ObjectOptimisticLockingFailureException(
                                Customer.class, UUID.randomUUID()));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getProperties()).containsEntry("code", "CONCURRENT_MODIFICATION");
        assertThat(problem.getDetail())
                .isEqualTo("La ressource a été modifiée en même temps, réessayez.");
    }

    @Test
    void uneErreurInattendueDonne500SansRienRevelerDeLException() throws Exception {
        var problem = handler.handleUnexpected(new IllegalStateException("secret interne"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(problem.getDetail()).doesNotContain("secret");
    }

    @Test
    void unRefusDAccesEstRelancePourSpringSecurity() {
        assertThatThrownBy(() -> handler.handleUnexpected(new AccessDeniedException("refusé")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unJetonInvalideDonne401InvalidToken() {
        var response = handler.handleInvalidToken(new InvalidTokenException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().getProperties()).containsEntry("code", "INVALID_TOKEN");
        assertThat(response.getHeaders().getFirst("WWW-Authenticate")).isEqualTo("Bearer");
    }
}
