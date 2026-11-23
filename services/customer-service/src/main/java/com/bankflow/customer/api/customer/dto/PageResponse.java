package com.bankflow.customer.api.customer.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/** Format de pagination commun aux services, indépendant de la sérialisation de Spring. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
