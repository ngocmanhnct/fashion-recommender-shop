/** Một ô nhập có nhãn và dòng báo lỗi bên dưới (lỗi lấy từ "errors" của backend). */
function FormField({ label, error, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

export default FormField;
