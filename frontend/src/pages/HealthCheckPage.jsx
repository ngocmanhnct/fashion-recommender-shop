import { useEffect, useState } from 'react';
import { getHealth } from '../services/healthApi';

function HealthCheckPage() {
  const [status, setStatus] = useState('Đang kiểm tra...');
  const [error, setError] = useState(null);

  useEffect(() => {
    getHealth()
      .then((data) => setStatus(data.status))
      .catch((err) => setError(err.message));
  }, []);

  return (
    <div>
      <h1>Kết nối Backend</h1>
      {error ? <p style={{ color: 'red' }}>Lỗi: {error}</p> : <p>Trạng thái backend: {status}</p>}
    </div>
  );
}

export default HealthCheckPage;
