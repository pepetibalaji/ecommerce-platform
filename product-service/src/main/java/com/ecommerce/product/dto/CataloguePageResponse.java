package com.ecommerce.product.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Explicit wire contract: independent of Spring Data PageImpl's internal serialization. */
public record CataloguePageResponse<T>(List<T> content, int number, int size, long totalElements,
                                        int totalPages, boolean first, boolean last, boolean empty,
                                        int numberOfElements) {
    public static <T> CataloguePageResponse<T> from(Page<T> page) {
        return new CataloguePageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(), page.isEmpty(),
                page.getNumberOfElements());
    }
}
