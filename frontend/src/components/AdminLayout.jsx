import { NavLink, Outlet, useLocation } from 'react-router';

/** Các mục quản trị, chia theo nhóm công việc. */
const SECTIONS = [
  {
    title: 'Cửa hàng',
    links: [
      { to: '/admin/products', label: 'Sản phẩm' },
      { to: '/admin/categories', label: 'Danh mục' },
      { to: '/admin/brands', label: 'Thương hiệu' },
    ],
  },
  { title: 'Bán hàng', links: [{ to: '/admin/orders', label: 'Đơn hàng' }] },
  {
    title: 'Khách hàng',
    links: [
      { to: '/admin/customers', label: 'Khách hàng' },
      { to: '/admin/segments', label: 'Phân nhóm' },
    ],
  },
];

/**
 * Khung trang quản trị: menu bên trái chia nhóm, nội dung từng mục bên phải. Chỉ phần nội dung
 * hiện dần khi đổi mục, menu đứng yên.
 */
function AdminLayout() {
  const { pathname } = useLocation();
  return (
    <div className="admin">
      <aside className="admin-nav" aria-label="Menu quản trị">
        <p className="admin-nav-heading">Quản trị</p>
        {SECTIONS.map((section) => (
          <div key={section.title} className="admin-nav-section">
            <p className="admin-nav-title">{section.title}</p>
            {section.links.map((link) => (
              <NavLink key={link.to} to={link.to}>
                {link.label}
              </NavLink>
            ))}
          </div>
        ))}
      </aside>
      <section key={pathname} className="admin-content page-transition">
        <Outlet />
      </section>
    </div>
  );
}

export default AdminLayout;
