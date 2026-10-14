package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.dto.BrandResponse;
import com.manhdn.fashionshop.entity.Brand;
import com.manhdn.fashionshop.exception.ConflictException;
import com.manhdn.fashionshop.repository.BrandRepository;
import com.manhdn.fashionshop.repository.ProductRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Nghiệp vụ quản lý thương hiệu (US-16). */
@Service
public class BrandService {

  private static final String DUPLICATE_NAME = "Tên thương hiệu đã tồn tại";

  private final BrandRepository brandRepository;
  private final ProductRepository productRepository;

  /** Nhận repository qua constructor. */
  public BrandService(BrandRepository brandRepository, ProductRepository productRepository) {
    this.brandRepository = brandRepository;
    this.productRepository = productRepository;
  }

  /**
   * Danh sách mọi thương hiệu, xếp theo tên.
   *
   * @return các thương hiệu
   */
  @Transactional(readOnly = true)
  public List<BrandResponse> findAll() {
    return brandRepository.findAll(Sort.by("name")).stream().map(BrandResponse::from).toList();
  }

  /**
   * Tạo thương hiệu. Tên trùng (so sánh theo collation của MySQL: không phân biệt hoa thường và
   * dấu) bị từ chối; ràng buộc UNIQUE trong CSDL chặn trường hợp 2 request đồng thời.
   *
   * @param name tên thương hiệu
   * @return thương hiệu vừa tạo
   * @throws ConflictException nếu tên đã tồn tại
   */
  @Transactional
  public BrandResponse create(String name) {
    String trimmed = name.trim();
    if (brandRepository.existsByName(trimmed)) {
      throw new ConflictException(DUPLICATE_NAME);
    }
    try {
      return BrandResponse.from(brandRepository.saveAndFlush(new Brand(trimmed)));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  /**
   * Đổi tên thương hiệu.
   *
   * @param id mã thương hiệu
   * @param name tên mới
   * @return thương hiệu sau khi đổi tên
   * @throws NoSuchElementException nếu không có thương hiệu
   * @throws ConflictException nếu tên mới trùng thương hiệu khác
   */
  @Transactional
  public BrandResponse rename(Long id, String name) {
    Brand brand = getEntity(id);
    String trimmed = name.trim();
    if (brandRepository.existsByNameAndIdNot(trimmed, id)) {
      throw new ConflictException(DUPLICATE_NAME);
    }
    brand.setName(trimmed);
    try {
      return BrandResponse.from(brandRepository.saveAndFlush(brand));
    } catch (DataIntegrityViolationException ex) {
      throw new ConflictException(DUPLICATE_NAME);
    }
  }

  /**
   * Xóa thương hiệu. Chỉ xóa được khi không còn sản phẩm nào thuộc thương hiệu, kể cả sản phẩm đã
   * xóa mềm, vì đơn hàng cũ vẫn tham chiếu tới các sản phẩm đó.
   *
   * @param id mã thương hiệu
   * @throws NoSuchElementException nếu không có thương hiệu
   * @throws ConflictException nếu thương hiệu còn sản phẩm
   */
  @Transactional
  public void delete(Long id) {
    Brand brand = getEntity(id);
    if (productRepository.existsByBrandId(id)) {
      throw new ConflictException("Thương hiệu còn sản phẩm, không xóa được");
    }
    brandRepository.delete(brand);
  }

  /** Lấy entity thương hiệu, dùng chung cho các service khác. */
  Brand getEntity(Long id) {
    return brandRepository
        .findById(id)
        .orElseThrow(() -> new NoSuchElementException("Không tìm thấy thương hiệu"));
  }
}
