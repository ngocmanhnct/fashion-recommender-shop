// Biểu tượng nét mảnh vẽ bằng SVG, không dùng thư viện icon. Luôn đi kèm chữ (hiện hoặc ẩn cho
// trình đọc màn hình), nên biểu tượng tự nó aria-hidden.
const PATHS = {
  bag: 'M6 8h12l-1 12H7L6 8Zm3 0V6a3 3 0 0 1 6 0v2',
  heart: 'M12 20s-7-4.4-7-10a4 4 0 0 1 7-2.6A4 4 0 0 1 19 10c0 5.6-7 10-7 10Z',
  receipt: 'M7 3h10v18l-2.5-1.5L12 21l-2.5-1.5L7 21V3Zm3 5h4m-4 4h4m-4 4h2',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-7 9a7 7 0 0 1 14 0',
  menu: 'M4 7h16M4 12h16M4 17h16',
  close: 'M6 6l12 12M18 6 6 18',
  search: 'M11 18a7 7 0 1 1 0-14 7 7 0 0 1 0 14Zm5-2 4 4',
  chevron: 'm6 9 6 6 6-6',
  arrow: 'M5 12h14m-5-5 5 5-5 5',
  box: 'M4 8l8-4 8 4v8l-8 4-8-4V8Zm0 0 8 4 8-4m-8 4v8',
  grid: 'M4 4h7v7H4V4Zm9 0h7v7h-7V4ZM4 13h7v7H4v-7Zm9 0h7v7h-7v-7Z',
  tag: 'M3 12V4h8l10 10-8 8-10-10Zm5-4.5h.01',
  users:
    'M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm-6 9a6 6 0 0 1 12 0m1-17a4 4 0 0 1 0 8m5 9a6 6 0 0 0-4-5.7',
  pie: 'M12 3a9 9 0 1 0 9 9h-9V3Zm3 0.5A9 9 0 0 1 20.5 9H15V3.5Z',
  ticket: 'M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4V7Zm10 0v10',
};

/**
 * Biểu tượng tên name: bag, heart, receipt, user, menu, close, search, chevron, arrow; menu quản
 * trị dùng thêm box, grid, tag, users, pie, ticket.
 */
function Icon({ name, size = 20, className = '' }) {
  return (
    <svg
      className={`icon ${className}`}
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d={PATHS[name]} />
    </svg>
  );
}

export default Icon;
