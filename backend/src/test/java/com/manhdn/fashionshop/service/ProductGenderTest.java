package com.manhdn.fashionshop.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.manhdn.fashionshop.entity.Gender;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductGenderTest {

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      value = {
        "Giày thể thao nam | Giày chạy bộ | MALE",
        "Sandal nữ | Sandal quai mảnh | FEMALE",
        "Dép nữ | Dép quai ngang nam nữ siêu nhẹ | -",
        "Balo thời trang | Balo Nam canvas có ngăn laptop | MALE",
        "Balo thời trang | Balo nữ mini dễ thương | FEMALE",
        "Balo thời trang | Balo Unisex đi học | -",
        "Kính mát | Kính râm cho phái mạnh | MALE",
        "Ví nữ | Ví cầm tay khóa nam châm | FEMALE",
        "Vali | Vali nhựa sản xuất tại Việt Nam | -",
        "Khăn | Khăn lụa | -",
        "Nón nam | - | MALE"
      })
  void genderComesFromNameThenCategory(String category, String name, Gender expected) {
    assertThat(ProductGender.of(category, name)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      nullValues = "-",
      value = {
        "FEMALE | Balo thời trang | Balo nam | false",
        "FEMALE | Balo thời trang | Balo nam nữ | true",
        "MALE | Balo thời trang | Balo nam | true",
        "MALE | Túi xách nữ | Túi da | false",
        "- | Túi xách nữ | Túi da | true",
        "OTHER | Túi xách nữ | Túi da | true"
      })
  void onlyTheOtherGenderIsLeftOut(Gender wanted, String category, String name, boolean fits) {
    assertThat(ProductGender.fits(wanted, category, name)).isEqualTo(fits);
  }
}
