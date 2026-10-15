/** Nút chuyển trang cho dữ liệu dạng PageResponse của backend (page đếm từ 0). */
function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button
        type="button"
        className="btn btn-ghost"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
      >
        ‹ Trước
      </button>
      <span className="muted">
        Trang {page + 1}/{totalPages}
      </span>
      <button
        type="button"
        className="btn btn-ghost"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        Sau ›
      </button>
    </div>
  );
}

export default Pagination;
