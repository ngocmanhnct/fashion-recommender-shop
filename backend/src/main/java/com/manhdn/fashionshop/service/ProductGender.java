package com.manhdn.fashionshop.service;

import com.manhdn.fashionshop.entity.Gender;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Giới tính của một sản phẩm, suy ra từ tên danh mục và tên sản phẩm (dùng cho gợi ý theo khảo
 * sát). Cùng quy tắc với {@code app/recommendation/gender.py} của dịch vụ học máy:
 *
 * <ol>
 *   <li>Tên sản phẩm ghi cả "nam" lẫn "nữ", hoặc "unisex": dùng chung (null).
 *   <li>Danh mục kết thúc bằng " nam" / " nữ": theo danh mục.
 *   <li>Danh mục dùng chung (Balo, Kính mát...): tên chỉ ghi "nam" (hoặc "phái mạnh") là đồ nam,
 *       chỉ ghi "nữ" (hoặc "phái đẹp") là đồ nữ, còn lại dùng chung.
 * </ol>
 *
 * <p>Bỏ các cụm có chữ "nam" không chỉ giới tính: "Việt Nam", "nam châm", "miền Nam".
 */
public final class ProductGender {

  private static final Pattern NOT_GENDER = Pattern.compile("việt nam|nam châm|miền nam");
  private static final Pattern MALE =
      Pattern.compile("\\bnam\\b|phái mạnh", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern FEMALE =
      Pattern.compile("\\bnữ\\b|phái đẹp", Pattern.UNICODE_CHARACTER_CLASS);
  private static final Pattern UNISEX =
      Pattern.compile("\\bunisex\\b", Pattern.UNICODE_CHARACTER_CLASS);

  private ProductGender() {}

  /**
   * Giới tính của sản phẩm.
   *
   * @param categoryName tên danh mục
   * @param productName tên sản phẩm
   * @return MALE, FEMALE, hoặc null nếu dùng chung
   */
  public static Gender of(String categoryName, String productName) {
    String name = NOT_GENDER.matcher(normalize(productName)).replaceAll(" ");
    boolean male = MALE.matcher(name).find();
    boolean female = FEMALE.matcher(name).find();
    if (UNISEX.matcher(name).find() || (male && female)) {
      return null;
    }
    String category = normalize(categoryName);
    if (category.endsWith(" nam")) {
      return Gender.MALE;
    }
    if (category.endsWith(" nữ")) {
      return Gender.FEMALE;
    }
    if (male) {
      return Gender.MALE;
    }
    return female ? Gender.FEMALE : null;
  }

  /**
   * Sản phẩm có hợp với giới tính khách chọn không: khách không chọn giới tính, hoặc sản phẩm dùng
   * chung, hoặc cùng giới tính.
   *
   * @param wanted giới tính khách chọn trong khảo sát, null nếu không chọn
   * @param categoryName tên danh mục
   * @param productName tên sản phẩm
   * @return true nếu được gợi ý
   */
  public static boolean fits(Gender wanted, String categoryName, String productName) {
    if (wanted != Gender.MALE && wanted != Gender.FEMALE) {
      return true;
    }
    Gender gender = of(categoryName, productName);
    return gender == null || gender == wanted;
  }

  private static String normalize(String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value, Normalizer.Form.NFC).toLowerCase(Locale.ROOT);
  }
}
