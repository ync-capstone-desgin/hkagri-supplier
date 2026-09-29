package com.hankooknongsan.supplier.common;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 목록 API의 공통 응답 형식: 현재 페이지 데이터, 페이지 번호(0부터), 페이지 크기, 전체 건수.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements) {

    /** 스프링 Page 결과를 화면용 DTO로 변환해 공통 응답 형식으로 만든다. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }
}
