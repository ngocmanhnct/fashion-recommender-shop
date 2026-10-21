package com.manhdn.fashionshop.repository;

import com.manhdn.fashionshop.entity.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** Truy cập bảng address (sổ địa chỉ). */
public interface AddressRepository extends JpaRepository<Address, Long> {

  /** Sổ địa chỉ của tài khoản: địa chỉ mặc định trước, sau đó mới thêm gần đây trước. */
  List<Address> findByAccountIdOrderByIsDefaultDescIdDesc(Long accountId);

  /** Địa chỉ theo mã, chỉ khi thuộc tài khoản này. */
  Optional<Address> findByIdAndAccountId(Long id, Long accountId);

  /** Số địa chỉ của tài khoản. */
  long countByAccountId(Long accountId);
}
