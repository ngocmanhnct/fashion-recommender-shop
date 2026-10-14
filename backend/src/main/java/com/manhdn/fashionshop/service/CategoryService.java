package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.CategoryResponse;
import com.manhdn.fashionshop.entity.Category;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.CategoryRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ quản lý danh mục sản phẩm (US-16). */
@Service
public class CategoryService {

  private static final String DUPLICATE_NAME = "Tên danh mục đã tồn tại";

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;

  /** Nhận repository qua constructor. */
  public CategoryService(
      CategoryRepository categoryRepository, ProductRepository productRepository) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
  }

  /**
   * Danh sách mọi danh mục, xếp theo tên.
   *
   * @return các danh mục
   */
  @Transactional(readOnly = true)
  public List<CategoryResponse> findAll() {
    return categoryRepository.findAll(Sort.by("name")).stream()
        .map(CategoryResponse::from)
        .toList();
  }

  /**
   * Tạo danh mục. Tên trùng (so sánh theo collation của MySQL: không phân biệt hoa thường và dấu)
   * bị từ chối; ràng buộc UNIQUE trong CSDL chặn trường hợp 2 request đồng thời.
   *
   * @param name tên danh mục
   * @return danh mục vừa tạo
   * @throws ConflictException nếu tên đã tồn tại
   */
  @Transactional
  public CategoryResponse create(String name) {
    String trimmed = name.trim();
    if (categoryRepository.existsByName(trimmed)) {
      throw new ConflictException(DUPLICATE_NAME);
    }
    try {
      return CategoryResponse.from(categoryRepository.saveAndFlush(new Category(trimmed)));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  /**
   * Đổi tên danh mục.
   *
   * @param id mã danh mục
   * @param name tên mới
   * @return danh mục sau khi đổi tên
   * @throws NoSuchElementException nếu không có danh mục
   * @throws ConflictException nếu tên mới trùng danh mục khác
   */
  @Transactional
  public CategoryResponse rename(Long id, String name) {
    Category category = getEntity(id);
    String trimmed = name.trim();
    if (categoryRepository.existsByNameAndIdNot(trimmed, id)) {
      throw new ConflictException(DUPLICATE_NAME);
    }
    category.setName(trimmed);
    try {
      return CategoryResponse.from(categoryRepository.saveAndFlush(category));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  /**
   * Xóa danh mục. Chỉ xóa được khi không còn sản phẩm nào thuộc danh mục, kể cả sản phẩm đã xóa
   * mềm, vì đơn hàng cũ vẫn tham chiếu tới các sản phẩm đó.
   *
   * @param id mã danh mục
   * @throws NoSuchElementException nếu không có danh mục
   * @throws ConflictException nếu danh mục còn sản phẩm
   */
  @Transactional
  public void delete(Long id) {
    Category category = getEntity(id);
    if (productRepository.existsByCategoryId(id)) {
      throw new ConflictException("Danh mục còn sản phẩm, không xóa được");
    }
    categoryRepository.delete(category);
  }

  /** Lấy entity danh mục, dùng chung cho các service khác. */
  Category getEntity(Long id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy danh mục"));
  }
}
