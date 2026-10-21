import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { MAX_KEYWORD_LENGTH, parseProductQuery, productsPath } from '../services/productQuery';

/**
 * Ô tìm sản phẩm trên thanh trên cùng của mọi trang (US-02). Ở trang /products ô hiện từ khóa
 * đang tìm: đổi từ khóa trên URL thì ô nhập được tạo lại (key), không đồng bộ bằng effect.
 */
function SearchBox() {
  const { pathname, search } = useLocation();
  const keyword =
    pathname === '/products' ? parseProductQuery(new URLSearchParams(search)).keyword : '';
  return <SearchForm key={keyword} initialKeyword={keyword} />;
}

function SearchForm({ initialKeyword }) {
  const navigate = useNavigate();
  const [value, setValue] = useState(initialKeyword);

  const handleSubmit = (event) => {
    event.preventDefault();
    navigate(productsPath({ keyword: value.trim() }));
  };

  return (
    <form className="search-box" onSubmit={handleSubmit}>
      <input
        type="search"
        value={value}
        onChange={(e) => setValue(e.target.value)}
        maxLength={MAX_KEYWORD_LENGTH}
        placeholder="Tìm giày, áo, túi xách..."
        aria-label="Tìm sản phẩm"
      />
      <button type="submit" className="btn btn-primary">
        Tìm
      </button>
    </form>
  );
}

export default SearchBox;
