package com.example.appShop.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Упаковщик страниц: Spring отдаёт Page<TShirt> в виде громоздкого JSON с пустыми
 * массивами и дублирующими полями — мы отдаём клиенту только нужное.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public static <S, T> PageResponse<T> from(Page<S> page, List<T> mapped) {
        return new PageResponse<>(mapped, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
