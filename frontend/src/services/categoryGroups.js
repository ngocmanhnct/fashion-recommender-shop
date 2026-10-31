// Gom danh mục thành vài nhóm lớn cho menu "Danh mục" và trang chủ. Danh mục chỉ có tên (không có
// cấp cha con trong CSDL), nên nhóm theo từ đầu tên; danh mục không khớp nhóm nào vào "Phụ kiện".

/** Nhóm theo thứ tự hiện trên menu; mỗi nhóm khớp danh mục có tên bắt đầu bằng 1 trong các từ. */
export const CATEGORY_GROUPS = [
  { key: 'giay', name: 'Giày', prefixes: ['giày'] },
  { key: 'dep', name: 'Dép & sandal', prefixes: ['dép', 'sandal'] },
  { key: 'tui', name: 'Túi & ví', prefixes: ['túi', 'ví', 'cặp'] },
  { key: 'balo', name: 'Balo & vali', prefixes: ['balo', 'vali'] },
  { key: 'phu-kien', name: 'Phụ kiện', prefixes: [] },
];

const FALLBACK_KEY = 'phu-kien';

/** Chữ thường dạng NFC: tên gõ trên macOS (NFD) vẫn khớp. */
function normalize(text) {
  return text.normalize('NFC').toLocaleLowerCase('vi');
}

/** Mã nhóm của 1 danh mục theo tên. */
export function groupKeyOf(categoryName) {
  const name = normalize(categoryName ?? '');
  const firstWord = name.split(/[\s,]+/)[0];
  const group = CATEGORY_GROUPS.find((g) => g.prefixes.includes(firstWord));
  return group ? group.key : FALLBACK_KEY;
}

/**
 * Chia danh sách danh mục [{id, name}] thành các nhóm [{key, name, categories}], giữ thứ tự danh
 * mục như API trả về; nhóm không có danh mục nào thì bỏ.
 */
export function groupCategories(categories) {
  const byKey = new Map(CATEGORY_GROUPS.map((g) => [g.key, []]));
  for (const category of categories ?? []) {
    byKey.get(groupKeyOf(category.name)).push(category);
  }
  return CATEGORY_GROUPS.map((g) => ({
    key: g.key,
    name: g.name,
    categories: byKey.get(g.key),
  })).filter((g) => g.categories.length > 0);
}
