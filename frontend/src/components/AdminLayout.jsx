import { NavLink, Outlet } from 'react-router';

/** Khung trang quản trị: menu bên trái, nội dung từng mục bên phải. */
function AdminLayout() {
  return (
    <div className="admin">
      <aside className="admin-nav">
        <NavLink to="/admin/products">Sản phẩm</NavLink>
        <NavLink to="/admin/categories">Danh mục</NavLink>
        <NavLink to="/admin/brands">Thương hiệu</NavLink>
        <NavLink to="/admin/orders">Đơn hàng</NavLink>
        <NavLink to="/admin/customers">Khách hàng</NavLink>
        <NavLink to="/admin/segments">Phân nhóm</NavLink>
      </aside>
      <section className="admin-content">
        <Outlet />
      </section>
    </div>
  );
}

export default AdminLayout;
