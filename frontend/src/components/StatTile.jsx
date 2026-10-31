/** Ô số liệu: nhãn, số lớn và 1 dòng ghi chú nhỏ. Đặt trong <dl className="stat-tiles">. */
function StatTile({ label, value, note }) {
  return (
    <div className="stat-tile">
      <dt>{label}</dt>
      <dd>
        <strong>{value}</strong>
        {note && <span className="muted small">{note}</span>}
      </dd>
    </div>
  );
}

export default StatTile;
