package com.manhdn.fashionshop.dto;

import com.manhdn.fashionshop.entity.Address;

/**
 * Một địa chỉ trong sổ địa chỉ.
 *
 * @param id mã địa chỉ
 * @param recipientName tên người nhận
 * @param phone số điện thoại
 * @param addressDetail địa chỉ chi tiết
 * @param isDefault có phải địa chỉ mặc định
 */
public record AddressResponse(
    long id, String recipientName, String phone, String addressDetail, boolean isDefault) {

  /** Chuyển entity sang DTO. */
  public static AddressResponse from(Address address) {
    return new AddressResponse(
        address.getId(),
        address.getRecipientName(),
        address.getPhone(),
        address.getAddressDetail(),
        address.isDefault());
  }
}
