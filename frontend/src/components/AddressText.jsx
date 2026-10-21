/** Tên, số điện thoại, địa chỉ của một địa chỉ đã lưu. */
function AddressText({ address }) {
  return (
    <div className="address-text">
      <p>
        <strong>{address.recipientName}</strong> · {address.phone}
        {address.isDefault && <span className="tag">Mặc định</span>}
      </p>
      <p className="muted small">{address.addressDetail}</p>
    </div>
  );
}

export default AddressText;
