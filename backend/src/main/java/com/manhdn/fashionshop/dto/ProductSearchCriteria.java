package com.manhdn.fashionshop.dto;

/**
 * Điều kiện tìm sản phẩm của trang người dùng (US-01, US-02). Trường null nghĩa là không lọc theo
 * điều kiện đó; service kiểm tra giới hạn của từng giá trị.
 *
 * @param keyword từ khóa, khớp tên sản phẩm, danh mục, thương hiệu; tối đa 100 ký tự
 * @param categoryId lọc theo danh mục
 * @param brandId lọc theo thương hiệu
 * @param minPrice giá thấp nhất, VNĐ, không âm
 * @param maxPrice giá cao nhất, VNĐ, không nhỏ hơn minPrice
 * @param minRating điểm đánh giá trung bình tối thiểu, 1 đến 5
 * @param sort cách sắp xếp
 * @param page số trang, từ 0 đến 10000
 * @param size số sản phẩm mỗi trang, 1 đến 48
 */
public record ProductSearchCriteria(
    String keyword,
    Long categoryId,
    Long brandId,
    Long minPrice,
    Long maxPrice,
    Integer minRating,
    ProductSort sort,
    int page,
    int size) {}
