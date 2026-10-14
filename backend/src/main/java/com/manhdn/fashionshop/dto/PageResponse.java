package com.manhdn.fashionshop.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Một trang kết quả. Dùng thay cho việc trả thẳng Page của Spring Data, vì cấu trúc JSON của Page
 * không ổn định giữa các phiên bản.
 *
 * @param content các phần tử của trang
 * @param page số trang, bắt đầu từ 0
 * @param size số phần tử tối đa mỗi trang
 * @param totalElements tổng số phần tử
 * @param totalPages tổng số trang
 * @param <T> kiểu phần tử
 */
public record PageResponse<T>(
    List<T> content, int page, int size, long totalElements, int totalPages) {

  /** Chuyển Page của Spring Data sang PageResponse, đổi từng phần tử bằng mapper. */
  public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
    return new PageResponse<>(
        page.getContent().stream().map(mapper).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages());
  }
}
