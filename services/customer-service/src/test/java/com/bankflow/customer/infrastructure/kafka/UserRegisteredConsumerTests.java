package com.bankflow.customer.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.bankflow.customer.application.customer.CustomerRegistrationService;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

class UserRegisteredConsumerTests {

    private final CustomerRegistrationService registrationService =
            mock(CustomerRegistrationService.class);
    private final UserRegisteredConsumer consumer =
            new UserRegisteredConsumer(registrationService, JsonMapper.builder().build());

    @Test
    void unConflitDUnicitePuisUnSuccesRejoueLeTraitementUneSeuleFois() {
        var userId = UUID.randomUUID();
        doThrow(new DataIntegrityViolationException("user_id en double"))
                .doNothing()
                .when(registrationService)
                .registerUser(eq(userId), eq("a@bankflow.test"), anyBoolean());

        consumer.consume(
                new ConsumerRecord<>(
                        "bank.users",
                        0,
                        0L,
                        userId.toString(),
                        """
                        {"eventType":"UserRegistered","eventVersion":1,
                         "payload":{"userId":"%s","email":"a@bankflow.test"}}\
                        """
                                .formatted(userId)));

        verify(registrationService, times(2))
                .registerUser(eq(userId), eq("a@bankflow.test"), eq(false));
    }

    @Test
    void unMessageIllisibleNAppelleJamaisLeService() {
        doNothing().when(registrationService).registerUser(any(), any(), anyBoolean());

        consumer.consume(new ConsumerRecord<>("bank.users", 0, 0L, "cle", "pas du json"));

        verify(registrationService, times(0)).registerUser(any(), any(), anyBoolean());
    }
}
