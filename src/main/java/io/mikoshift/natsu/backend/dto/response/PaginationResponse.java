package io.mikoshift.natsu.backend.dto.response;

import org.springframework.data.domain.Page;

public record PaginationResponse(int page, int perPage, long totalCount, int totalPages) {

    public static PaginationResponse from(Page<?> page) {
        return new PaginationResponse(page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
