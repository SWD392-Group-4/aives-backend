package com.aives.common.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ket qua phan trang tra ve cho FE. page bat dau tu 0.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> items;
    private int page;
    private int size;
    private long totalItems;
    private int totalPages;
}
