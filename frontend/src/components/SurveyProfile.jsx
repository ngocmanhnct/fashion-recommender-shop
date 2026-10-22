import ProductGrid from './ProductGrid';

/**
 * Kết quả khảo sát (US-23): kiểu người mua, tóm tắt sở thích và sản phẩm gợi ý kèm lý do.
 * evaluation: { profile, products } trả về từ /api/survey/evaluate.
 */
function SurveyProfile({ evaluation, heading = 'h2' }) {
  const { profile, products } = evaluation;
  const Heading = heading;
  const reasons = Object.fromEntries(products.map((item) => [item.product.id, item.reason]));
  return (
    <div className="survey-profile">
      <Heading>{profile.styleName}</Heading>
      <p className="muted">{profile.summary}</p>
      {products.length > 0 ? (
        <ProductGrid products={products.map((item) => item.product)} reasons={reasons} />
      ) : (
        <p className="muted">Chưa có sản phẩm phù hợp, hãy xem các sản phẩm bán chạy bên dưới.</p>
      )}
    </div>
  );
}

export default SurveyProfile;
