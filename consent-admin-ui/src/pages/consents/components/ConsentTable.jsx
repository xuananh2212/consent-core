import { consentColumns } from "./columns.jsx";

export default function ConsentTable({ items, selectedId, busy, onSelect, onRevoke }) {
  if (!items.length) return <div className="empty">Không có consent phù hợp bộ lọc.</div>;
  const columns = consentColumns({ busy, onRevoke });
  return (
    <table>
      <thead>
        <tr>
          {columns.map((column) => <th key={column.key}>{column.title}</th>)}
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr
            key={item.id}
            className={item.id === selectedId ? "active" : undefined}
            onClick={() => onSelect(item.id)}
          >
            {columns.map((column) => (
              <td key={column.key} className={column.className}>
                {column.render(item)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
