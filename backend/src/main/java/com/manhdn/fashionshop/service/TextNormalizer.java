package com.manhdn.fashionshop.service;

import java.text.Normalizer;

/**
 * Chuẩn hóa chữ trước khi lưu tên, mô tả.
 *
 * <p>Chữ tiếng Việt có thể đến ở dạng tách dấu (Unicode NFD: chữ cái gốc + ký tự dấu riêng, vd gõ
 * trên macOS hoặc chép từ dữ liệu Tiki). Collation của MySQL coi 2 dạng là một khi so bằng, nhưng
 * {@code LIKE} so từng ký tự và {@code REGEXP_REPLACE} tách ký tự dấu thành ranh giới từ, nên tên
 * lưu ở dạng tách dấu không tìm được bằng từ khóa. Vì vậy mọi tên, mô tả đều lưu ở dạng dựng sẵn
 * (NFC), cùng dạng với từ khóa tìm kiếm.
 */
final class TextNormalizer {

  private TextNormalizer() {}

  /** Chuỗi ở dạng Unicode NFC; null giữ nguyên null. */
  static String nfc(String text) {
    return text == null ? null : Normalizer.normalize(text, Normalizer.Form.NFC);
  }
}
