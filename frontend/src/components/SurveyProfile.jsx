import RecommendationGrid from './RecommendationGrid';

/**
 * Kết quả khảo sát (US-23): kiểu người mua, tóm tắt sở thích và sản phẩm gợi ý kèm lý do.
 * evaluation: { profile, products } trả về từ /api/survey/evaluate.
 * placement: vị trí khối gợi ý khi ghi lượt hiện, lượt bấm (HOME ở trang chủ, SURVEY_RESULT ở trang
 * khảo sát).
 */
function SurveyProfile({ evaluation, heading = 'h2', placement }) {
  const { profile, products } = evaluation;
  const Heading = heading;
  return (
    <div className="survey-profile">
      <Heading>{profile.styleName}</Heading>
      <p className="muted">{profile.summary}</p>
      {products.length > 0 ? (
        <RecommendationGrid items={products} placement={placement} />
      ) : (
        <p className="muted">Chưa có sản phẩm phù hợp, hãy xem các sản phẩm bán chạy bên dưới.</p>
      )}
    </div>
  );
}

export default SurveyProfile;
