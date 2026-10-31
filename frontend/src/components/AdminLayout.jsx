import { NavLink, Outlet, useLocation } from 'react-router';
import { ADMIN_SECTIONS, findAdminLocation } from '../services/adminMenu';
import Icon from './Icon';

/**
 * Khung trang quản trị: menu bên trái chia nhóm (mỗi nhóm 1 khối riêng, mục có biểu tượng và
 * dòng mô tả), nội dung bên phải có dòng "đang ở đâu" (nhóm › mục). Chỉ phần nội dung hiện dần
 * khi đổi mục, menu đứng yên.
 */
function AdminLayout() {
  const { pathname } = useLocation();
  const current = findAdminLocation(pathname);
  return (
    <div className="admin">
      <aside className="admin-nav" aria-label="Menu quản trị">
        <p className="admin-nav-heading">Quản trị</p>
        {ADMIN_SECTIONS.map((section) => (
          <section
            key={section.key}
            className="admin-nav-section"
            aria-labelledby={`admin-nav-${section.key}`}
          >
            <h2 id={`admin-nav-${section.key}`} className="admin-nav-title">
              {section.title}
            </h2>
            <ul>
              {section.links.map((link) => (
                <li key={link.to}>
                  <NavLink to={link.to} end={link.end} className="admin-nav-link">
                    <Icon name={link.icon} size={18} />
                    <span className="admin-nav-text">
                      {link.label}
                      <small>{link.hint}</small>
                    </span>
                  </NavLink>
                </li>
              ))}
            </ul>
          </section>
        ))}
      </aside>
      <section key={pathname} className="admin-content page-transition">
        {current && (
          <nav className="admin-breadcrumb" aria-label="Vị trí trong trang quản trị">
            <ol>
              <li>{current.section.title}</li>
              <li aria-current="page">{current.link.label}</li>
            </ol>
          </nav>
        )}
        <Outlet />
      </section>
    </div>
  );
}

export default AdminLayout;
