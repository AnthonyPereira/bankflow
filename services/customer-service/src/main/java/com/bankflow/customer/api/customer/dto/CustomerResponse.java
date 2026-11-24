package com.bankflow.customer.api.customer.dto;

import com.bankflow.customer.domain.customer.Customer;
import com.bankflow.customer.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID userId,
        String email,
        String firstName,
        String lastName,
        String phone,
        String address,
        CustomerStatus status,
        boolean demo,
        Instant createdAt,
        Instant updatedAt) {

    public static CustomerResponse createFrom(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getUserId(),
                customer.getEmail(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getStatus(),
                customer.isDemo(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
