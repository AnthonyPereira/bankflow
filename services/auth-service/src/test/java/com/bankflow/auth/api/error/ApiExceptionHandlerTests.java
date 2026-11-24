package com.bankflow.auth.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.bankflow.auth.api.auth.RefreshCookies;
import com.bankflow.auth.domain.user.exception.DemoAccountCannotBeDisabledException;
import org.junit.jupiter.api.Test;

class ApiExceptionHandlerTests {

    private final ApiExceptionHandler handler = new ApiExceptionHandler(new RefreshCookies(true));

    @Test
    void laDesactivationDUnCompteDeDemonstrationRepond409() {
        var problem =
                handler.handleDemoAccountCannotBeDisabled(
                        new DemoAccountCannotBeDisabledException());

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getDetail())
                .isEqualTo("Un compte de démonstration ne peut pas être désactivé.");
    }
}
