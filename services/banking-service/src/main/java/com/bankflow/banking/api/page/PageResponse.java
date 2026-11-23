package com.bankflow.banking.api.page;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** Page de résultats au format de l'API, indépendant de la sérialisation de Spring Data. */
public record PageResponse<T>(
        List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> createFrom(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber(),
                source.getSize(),
                source.getTotalElements(),
                source.getTotalPages());
    }
}
