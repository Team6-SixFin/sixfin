package com.sparta.trading.global.response;

import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
@Builder
public class PageInfo {

    private String paginationType;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;

    public static PageInfo of(Page<?> page) {
        return PageInfo.builder()
                .paginationType("OFFSET")
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }
}
