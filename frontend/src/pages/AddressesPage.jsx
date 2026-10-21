import { useState } from 'react';
import AddressForm from '../components/AddressForm';
import AddressText from '../components/AddressText';
import { useApiData } from '../hooks/useApiData';
import { createAddress, deleteAddress, listAddresses, updateAddress } from '../services/addressApi';
import { getErrorMessage } from '../services/errorMessage';

/**
 * Trang sổ địa chỉ /addresses (US-09): thêm, sửa, xóa, đặt địa chỉ mặc định. Đơn đã đặt giữ bản
 * sao địa chỉ nên sửa, xóa ở đây không làm đổi đơn cũ (BR-29).
 */
function AddressesPage() {
  const { data, lastData, error, reload } = useApiData('addresses', listAddresses);
  // null: không mở form; 'new': thêm mới; số: đang sửa địa chỉ có mã đó.
  const [editing, setEditing] = useState(null);
  const [actionError, setActionError] = useState('');
  const addresses = data ?? lastData;

  const handleSaved = () => {
    setEditing(null);
    reload();
  };

  const run = async (action) => {
    setActionError('');
    try {
      await action();
      reload();
    } catch (err) {
      setActionError(getErrorMessage(err, 'Không cập nhật được sổ địa chỉ'));
    }
  };

  const handleDelete = async (address) => {
    if (!window.confirm(`Xóa địa chỉ của ${address.recipientName}?`)) return;
    await run(() => deleteAddress(address.id));
  };

  const handleSetDefault = async (address) => {
    await run(() => updateAddress(address.id, { ...address, isDefault: true }));
  };

  return (
    <div className="narrow-page">
      <h1>Sổ địa chỉ</h1>
      {error && !addresses && (
        <div className="alert alert-error">
          <p>{getErrorMessage(error, 'Không tải được sổ địa chỉ')}</p>
          <button type="button" className="btn btn-ghost" onClick={reload}>
            Thử lại
          </button>
        </div>
      )}
      {!addresses && !error && <p className="muted">Đang tải...</p>}
      {actionError && (
        <p className="alert alert-error" role="alert">
          {actionError}
        </p>
      )}
      {addresses && (
        <ul className="address-list">
          {addresses.length === 0 && <li className="muted">Bạn chưa lưu địa chỉ nào.</li>}
          {addresses.map((address) =>
            editing === address.id ? (
              <li key={address.id} className="address-card">
                <AddressForm
                  initial={address}
                  submitLabel="Lưu thay đổi"
                  onSubmit={(values) => updateAddress(address.id, values).then(handleSaved)}
                  onCancel={() => setEditing(null)}
                />
              </li>
            ) : (
              <li key={address.id} className="address-card">
                <AddressText address={address} />
                <div className="address-actions">
                  <button
                    type="button"
                    className="btn-link small"
                    onClick={() => setEditing(address.id)}
                  >
                    Sửa
                  </button>
                  {!address.isDefault && (
                    <button
                      type="button"
                      className="btn-link small"
                      onClick={() => handleSetDefault(address)}
                    >
                      Đặt làm mặc định
                    </button>
                  )}
                  <button
                    type="button"
                    className="btn-link small"
                    onClick={() => handleDelete(address)}
                  >
                    Xóa
                  </button>
                </div>
              </li>
            )
          )}
        </ul>
      )}
      {addresses &&
        (editing === 'new' ? (
          <div className="address-card">
            <h2>Thêm địa chỉ</h2>
            <AddressForm
              submitLabel="Thêm địa chỉ"
              onSubmit={(values) => createAddress(values).then(handleSaved)}
              onCancel={() => setEditing(null)}
            />
          </div>
        ) : (
          <button type="button" className="btn btn-ghost" onClick={() => setEditing('new')}>
            + Thêm địa chỉ
          </button>
        ))}
    </div>
  );
}

export default AddressesPage;
