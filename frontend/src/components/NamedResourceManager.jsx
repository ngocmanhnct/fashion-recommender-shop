import { useCallback, useEffect, useState } from 'react';
import { getErrorMessage } from '../services/errorMessage';

/**
 * Bảng quản lý cho loại dữ liệu chỉ có tên (danh mục, thương hiệu): xem, thêm, đổi tên, xóa.
 * Hai trang dùng chung component này, chỉ khác tiêu đề và hàm gọi API.
 */
function NamedResourceManager({ title, noun, loadAll, api }) {
  const [items, setItems] = useState([]);
  const [newName, setNewName] = useState('');
  const [editingId, setEditingId] = useState(null);
  const [editingName, setEditingName] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  const reload = useCallback(() => {
    loadAll()
      .then(setItems)
      .catch((err) => setError(getErrorMessage(err)));
  }, [loadAll]);

  useEffect(reload, [reload]);

  const run = async (action, successMessage) => {
    setError('');
    setMessage('');
    try {
      await action();
      setMessage(successMessage);
      reload();
      return true;
    } catch (err) {
      setError(getErrorMessage(err));
      return false;
    }
  };

  const handleCreate = async (event) => {
    event.preventDefault();
    const name = newName.trim();
    if (!name) return;
    if (await run(() => api.create(name), `Đã thêm ${noun} "${name}"`)) {
      setNewName('');
    }
  };

  const handleRename = async (id) => {
    const name = editingName.trim();
    if (!name) return;
    if (await run(() => api.rename(id, name), `Đã đổi tên thành "${name}"`)) {
      setEditingId(null);
    }
  };

  const handleDelete = (item) => {
    if (window.confirm(`Xóa ${noun} "${item.name}"?`)) {
      run(() => api.remove(item.id), `Đã xóa ${noun} "${item.name}"`);
    }
  };

  return (
    <div>
      <h1>{title}</h1>
      <form className="inline-form" onSubmit={handleCreate}>
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          placeholder={`Tên ${noun} mới`}
          maxLength={100}
        />
        <button type="submit" className="btn btn-primary">
          Thêm
        </button>
      </form>
      {error && <p className="alert alert-error">{error}</p>}
      {message && <p className="alert alert-success">{message}</p>}

      <table className="table">
        <thead>
          <tr>
            <th>ID</th>
            <th>Tên</th>
            <th className="actions">Thao tác</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr key={item.id}>
              <td>{item.id}</td>
              <td>
                {editingId === item.id ? (
                  <input
                    value={editingName}
                    onChange={(e) => setEditingName(e.target.value)}
                    maxLength={100}
                    autoFocus
                  />
                ) : (
                  item.name
                )}
              </td>
              <td className="actions">
                {editingId === item.id ? (
                  <>
                    <button
                      type="button"
                      className="btn btn-primary"
                      onClick={() => handleRename(item.id)}
                    >
                      Lưu
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => setEditingId(null)}
                    >
                      Hủy
                    </button>
                  </>
                ) : (
                  <>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      onClick={() => {
                        setEditingId(item.id);
                        setEditingName(item.name);
                      }}
                    >
                      Đổi tên
                    </button>
                    <button
                      type="button"
                      className="btn btn-danger"
                      onClick={() => handleDelete(item)}
                    >
                      Xóa
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
          {items.length === 0 && (
            <tr>
              <td colSpan={3} className="muted">
                Chưa có {noun} nào.
              </td>
            </tr>
          )}
        </tbody>
      </table>
    </div>
  );
}

export default NamedResourceManager;
