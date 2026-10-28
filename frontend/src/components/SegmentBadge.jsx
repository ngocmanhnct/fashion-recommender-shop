/** Nhãn nhóm khách hàng (US-19); chưa được phân nhóm thì hiện theo BR-24. */
function SegmentBadge({ segment }) {
  if (!segment) {
    return <span className="segment-badge segment-none">Khách hàng mới / chưa đủ dữ liệu</span>;
  }
  return (
    <span className={`segment-badge segment-${segment.code.toLowerCase().replaceAll('_', '-')}`}>
      {segment.name}
    </span>
  );
}

export default SegmentBadge;
