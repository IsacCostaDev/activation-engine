package com.activation.engine.application.dto;

import java.util.List;

public record PagedResponseDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public static <T> PagedResponseDto<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / (double) size);
        return new PagedResponseDto<>(content, page, size, totalElements, totalPages);
    }
}
