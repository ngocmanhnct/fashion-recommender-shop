import { Link } from 'react-router';
import Icon from '../components/Icon';
import OffersBanner from '../components/OffersBanner';
import PersonalRecommendations from '../components/PersonalRecommendations';
import ProductArt from '../components/ProductArt';
import ProductImage from '../components/ProductImage';
import ProductSection from '../components/ProductSection';
import Reveal from '../components/Reveal';
import { useApiData } from '../hooks/useApiData';
import { useAuth } from '../hooks/useAuth';
import { getCategories } from '../services/catalogApi';
import { groupCategories } from '../services/categoryGroups';
import { formatNumber, formatPriceRange } from '../services/format';
import { loadProductPage, productsPath } from '../services/productQuery';

// Cùng truy vấn với khối "Sản phẩm bán chạy" bên dưới (sắp xếp phổ biến, 8 sản phẩm).
const POPULAR_KEY = new URLSearchParams({ sort: 'POPULAR', size: '8' }).toString();
// Số danh mục con hiện trên mỗi thẻ nhóm; còn lại ghi "và N danh mục khác".
const GROUP_PREVIEW = 4;

/**
 * Trang chủ: phần mở đầu (lời giới thiệu + 3 sản phẩm bán chạy nhất xếp chồng), dải báo mã giảm giá
 * được tặng (nếu có), khối "Gợi ý cho
 * bạn" (US-13; chưa có gợi ý riêng thì lời mời làm khảo sát hoặc gợi ý theo khảo sát, US-23), các
 * nhóm danh mục, khối "Sản phẩm bán chạy" (BR-22) và phần giải thích cách gợi ý hoạt động.
 */
function HomePage() {
  const { user } = useAuth();
  const popular = useApiData(POPULAR_KEY, loadProductPage);
  const total = popular.data?.totalElements;

  return (
    <div className="home">
      <section className="home-hero">
        <div className="home-hero-text">
          <p className="eyebrow">Giày · Túi · Phụ kiện</p>
          <h1>Đồ hợp với bạn, tìm thấy nhanh hơn.</h1>
          <p className="home-hero-lead">
            {total > 0 && `${formatNumber(total)} sản phẩm đang bán. `}
            Cửa hàng ghi nhận những gì bạn xem và mua để gợi ý đúng món bạn cần.
          </p>
          <div className="home-hero-actions">
            <Link to="/products" className="btn btn-primary btn-lg">
              Xem sản phẩm
              <Icon name="arrow" size={18} />
            </Link>
            {user?.role === 'ADMIN' ? (
              <Link to="/admin" className="btn btn-ghost btn-lg">
                Vào trang quản trị
              </Link>
            ) : (
              <a href="#cach-goi-y" className="btn btn-ghost btn-lg">
                Gợi ý hoạt động thế nào?
              </a>
            )}
          </div>
        </div>
        <HeroCollage products={popular.data?.content?.slice(0, 3)} />
      </section>

      <OffersBanner />
      <Reveal>
        <PersonalRecommendations />
      </Reveal>
      <Reveal>
        <CategoryGroups />
      </Reveal>
      <Reveal>
        <ProductSection title="Sản phẩm bán chạy" sort="POPULAR" size={8} moreLink="/products" />
      </Reveal>
      <Reveal>
        <HowItWorks />
      </Reveal>
    </div>
  );
}

/**
 * 3 sản phẩm bán chạy nhất xếp chồng nghiêng như ảnh dán trên bàn. Lúc đang tải là 3 khung trống
 * cùng chỗ để trang không bị giật khi ảnh hiện ra.
 */
function HeroCollage({ products }) {
  if (!products || products.length === 0) {
    return (
      <div className="hero-collage is-empty" aria-hidden="true">
        <span className="hero-card" />
        <span className="hero-card" />
        <span className="hero-card" />
      </div>
    );
  }
  return (
    <ol className="hero-collage" aria-label="Bán chạy nhất">
      {products.map((product, index) => (
        <li key={product.id} className="hero-card">
          <Link to={`/products/${product.id}`}>
            <ProductImage
              src={product.thumbnailUrl}
              productId={product.id}
              categoryName={product.categoryName}
            />
            <span className="hero-card-caption">
              <span className="hero-card-rank">{String(index + 1).padStart(2, '0')}</span>
              <span className="hero-card-name">{product.name}</span>
              <span className="hero-card-price">
                {formatPriceRange(product.minPrice, product.maxPrice)}
              </span>
            </span>
          </Link>
        </li>
      ))}
    </ol>
  );
}

/** Các nhóm danh mục, mỗi nhóm 1 thẻ kèm vài danh mục con; ẩn khi chưa tải được danh mục. */
function CategoryGroups() {
  const { data } = useApiData('all', getCategories);
  const groups = groupCategories(data);
  if (groups.length === 0) return null;
  return (
    <section className="product-section">
      <div className="section-header">
        <h2>Mua theo nhóm</h2>
        <Link to="/products">Tất cả danh mục ›</Link>
      </div>
      <ul className="group-grid">
        {groups.map((group, index) => {
          const shown = group.categories.slice(0, GROUP_PREVIEW);
          const more = group.categories.length - shown.length;
          return (
            <li key={group.key} className="group-card">
              <ProductArt productId={index * 7 + 3} categoryName={group.categories[0].name} />
              <div className="group-card-body">
                <h3>{group.name}</h3>
                <ul className="group-card-links">
                  {shown.map((category) => (
                    <li key={category.id}>
                      <Link to={productsPath({ categoryId: String(category.id) })}>
                        {category.name}
                      </Link>
                    </li>
                  ))}
                </ul>
                {more > 0 && <p className="muted small">+ {more} danh mục khác</p>}
              </div>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

const STEPS = [
  {
    title: 'Bạn xem, thêm vào giỏ, mua hàng',
    text: 'Mỗi thao tác khi đã đăng nhập được ghi lại cho riêng tài khoản của bạn.',
  },
  {
    title: 'Hệ thống tính lại gợi ý',
    text: 'Kết hợp sản phẩm tương tự, sản phẩm hay được mua cùng nhau và câu trả lời khảo sát.',
  },
  {
    title: 'Trang chủ đổi theo bạn',
    text: 'Trong vòng khoảng nửa phút, trang chủ hiện gợi ý mới kèm lý do được gợi ý.',
  },
];

/** Giải thích ngắn cách gợi ý hoạt động (đích của nút "Gợi ý hoạt động thế nào?"). */
function HowItWorks() {
  return (
    <section id="cach-goi-y" className="how-it-works">
      <p className="eyebrow">Gợi ý sản phẩm</p>
      <h2>Càng mua sắm, gợi ý càng đúng ý bạn</h2>
      <ol className="steps">
        {STEPS.map((step, index) => (
          <li key={step.title}>
            <span className="step-number">{String(index + 1).padStart(2, '0')}</span>
            <h3>{step.title}</h3>
            <p>{step.text}</p>
          </li>
        ))}
      </ol>
      <Link to="/survey" className="btn btn-ghost">
        Làm khảo sát sở thích
      </Link>
    </section>
  );
}

export default HomePage;
