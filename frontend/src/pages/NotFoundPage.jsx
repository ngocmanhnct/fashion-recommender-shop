import { Link } from 'react-router';

function NotFoundPage() {
  return (
    <div className="hero">
      <h1>Không tìm thấy trang</h1>
      <p>
        <Link to="/">Về trang chủ</Link>
      </p>
    </div>
  );
}

export default NotFoundPage;
