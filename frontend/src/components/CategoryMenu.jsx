import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router';
import { useApiData } from '../hooks/useApiData';
import { getCategories } from '../services/catalogApi';
import { groupCategories } from '../services/categoryGroups';
import { productsPath } from '../services/productQuery';
import Icon from './Icon';

/**
 * Hàng menu danh mục dưới thanh trên cùng (màn hình rộng): mỗi nhóm danh mục là 1 nút mở bảng
 * các danh mục con. Mở bằng rê chuột hoặc bấm; Esc, bấm ra ngoài hay chuyển trang thì đóng.
 */
function CategoryMenu() {
  const { data } = useApiData('all', getCategories);
  const location = useLocation();
  // Bảng đang mở gắn với lượt chuyển trang lúc mở: sang trang khác (location.key đổi) là tự đóng,
  // không cần effect đặt lại state.
  const [opened, setOpened] = useState(null);
  const current = opened?.locationKey === location.key ? opened : null;
  const openKey = current?.groupKey ?? null;
  const rootRef = useRef(null);
  const groups = groupCategories(data);

  const open = (groupKey, byHover) => setOpened({ groupKey, byHover, locationKey: location.key });
  const close = () => setOpened(null);
  // Rê chuột vào là đã mở, nên cú bấm ngay sau đó giữ bảng mở (không đóng mất); bảng mở bằng cú
  // bấm thì bấm lần nữa mới đóng.
  const handleClick = (groupKey) => {
    if (openKey === groupKey && !current.byHover) close();
    else open(groupKey, false);
  };
  const handleHover = (groupKey) => {
    if (openKey !== groupKey) open(groupKey, true);
  };

  useEffect(() => {
    if (openKey === null) return undefined;
    const handlePointer = (event) => {
      if (!rootRef.current?.contains(event.target)) setOpened(null);
    };
    document.addEventListener('pointerdown', handlePointer);
    return () => document.removeEventListener('pointerdown', handlePointer);
  }, [openKey]);

  const handleKeyDown = (event) => {
    if (event.key === 'Escape' && openKey !== null) {
      rootRef.current?.querySelector(`[data-group="${openKey}"]`)?.focus();
      close();
    }
  };

  return (
    <nav
      className="category-menu"
      aria-label="Danh mục sản phẩm"
      ref={rootRef}
      onMouseLeave={close}
      onKeyDown={handleKeyDown}
    >
      <div className="container category-menu-inner">
        <NavLink to="/products" end className="category-menu-link">
          Tất cả sản phẩm
        </NavLink>
        {groups.map((group) => {
          const isOpen = openKey === group.key;
          const panelId = `menu-${group.key}`;
          return (
            <div
              key={group.key}
              className="category-menu-item"
              onMouseEnter={() => handleHover(group.key)}
            >
              <button
                type="button"
                className={isOpen ? 'category-menu-link is-open' : 'category-menu-link'}
                data-group={group.key}
                aria-expanded={isOpen}
                aria-controls={panelId}
                onClick={() => handleClick(group.key)}
              >
                {group.name}
                <Icon name="chevron" size={14} />
              </button>
              <div id={panelId} className="category-panel" hidden={!isOpen}>
                <p className="category-panel-title">{group.name}</p>
                <ul>
                  {group.categories.map((category) => (
                    <li key={category.id}>
                      <Link to={productsPath({ categoryId: String(category.id) })} onClick={close}>
                        {category.name}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          );
        })}
        <NavLink to="/survey" className="category-menu-link category-menu-survey">
          Khảo sát sở thích
        </NavLink>
      </div>
    </nav>
  );
}

export default CategoryMenu;
