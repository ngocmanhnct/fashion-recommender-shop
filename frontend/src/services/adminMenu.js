// Menu trang quản trị: nhóm, mục, và cách tìm mục của đường dẫn hiện tại.

/**
 * Các mục quản trị, chia theo nhóm công việc. Tên mục không trùng tên nhóm (nhóm "Khách hàng" có
 * "Danh sách khách hàng", không có mục "Khách hàng"), mỗi mục có 1 dòng nói nó dùng để làm gì.
 */
export const ADMIN_SECTIONS = [
  {
    key: 'tong-quan',
    title: 'Tổng quan',
    links: [
      {
        to: '/admin',
        label: 'Bảng điều khiển',
        hint: 'Doanh thu, đơn hàng, gợi ý',
        icon: 'gauge',
        end: true,
      },
    ],
  },
  {
    key: 'cua-hang',
    title: 'Cửa hàng',
    links: [
      { to: '/admin/products', label: 'Sản phẩm', hint: 'Thêm, sửa, ngừng bán', icon: 'box' },
      { to: '/admin/categories', label: 'Danh mục', hint: 'Nhóm hàng trên menu', icon: 'grid' },
      { to: '/admin/brands', label: 'Thương hiệu', hint: 'Hãng của sản phẩm', icon: 'tag' },
    ],
  },
  {
    key: 'ban-hang',
    title: 'Bán hàng',
    links: [
      {
        to: '/admin/orders',
        label: 'Đơn hàng',
        hint: 'Duyệt, giao, hủy, hoàn tiền',
        icon: 'receipt',
      },
      {
        to: '/admin/vouchers',
        label: 'Mã giảm giá',
        hint: 'Tạo mã, hạn dùng, số lượt',
        icon: 'ticket',
      },
    ],
  },
  {
    key: 'khach-hang',
    title: 'Khách hàng',
    links: [
      {
        to: '/admin/customers',
        label: 'Danh sách khách hàng',
        hint: 'Tra cứu từng khách',
        icon: 'users',
      },
      {
        to: '/admin/segments',
        label: 'Phân nhóm khách hàng',
        hint: 'Hồ sơ, khách cần chăm sóc',
        icon: 'pie',
      },
    ],
  },
  {
    key: 'goi-y',
    title: 'Gợi ý sản phẩm',
    links: [
      {
        to: '/admin/recommendations',
        label: 'Hiệu quả gợi ý',
        hint: 'Thử nghiệm A/B, tỷ lệ bấm',
        icon: 'chart',
      },
    ],
  },
];

/**
 * Nhóm và mục quản trị của đường dẫn hiện tại (kể cả trang con như /admin/orders/5). Mục có
 * end: true (Bảng điều khiển ở /admin) chỉ khớp đúng đường dẫn của nó, không khớp trang con.
 */
export function findAdminLocation(pathname) {
  const path = pathname.length > 1 ? pathname.replace(/\/$/, '') : pathname;
  for (const section of ADMIN_SECTIONS) {
    const link = section.links.find(
      (l) => path === l.to || (!l.end && path.startsWith(`${l.to}/`))
    );
    if (link) return { section, link };
  }
  return null;
}
