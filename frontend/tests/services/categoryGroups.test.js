import { describe, expect, it } from 'vitest';
import { groupCategories, groupKeyOf } from '../../src/services/categoryGroups';

describe('categoryGroups', () => {
  it('xếp danh mục vào nhóm theo từ đầu tên, không phân biệt hoa thường và NFD', () => {
    expect(groupKeyOf('Giày boots nam')).toBe('giay');
    expect(groupKeyOf('Giày lười nữ'.normalize('NFD'))).toBe('giay');
    expect(groupKeyOf('sandal nữ')).toBe('dep');
    expect(groupKeyOf('Cặp, túi chống sốc laptop')).toBe('tui');
    expect(groupKeyOf('Vali')).toBe('balo');
    expect(groupKeyOf('Phụ kiện, vệ sinh giày')).toBe('phu-kien');
    expect(groupKeyOf(undefined)).toBe('phu-kien');
  });

  it('giữ thứ tự danh mục, bỏ nhóm rỗng', () => {
    const groups = groupCategories([
      { id: 1, name: 'Ví nam' },
      { id: 2, name: 'Giày tây nam' },
      { id: 3, name: 'Khăn' },
      { id: 4, name: 'Túi tote nữ' },
    ]);
    expect(groups.map((g) => [g.key, g.categories.map((c) => c.id)])).toEqual([
      ['giay', [2]],
      ['tui', [1, 4]],
      ['phu-kien', [3]],
    ]);
    expect(groupCategories(null)).toEqual([]);
  });
});
