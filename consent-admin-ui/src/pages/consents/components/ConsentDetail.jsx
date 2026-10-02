import { detailFields } from "./columns.jsx";
import { REVOKABLE } from "../constants.js";
import RevokeButton from "./RevokeButton.jsx";

export default function ConsentDetail({ item, busy, onRevoke }) {
  return (
    <section className="detail">
      <div className="detail-head">
        <h2>{item.purpose || item.consentType}</h2>
        {REVOKABLE.has(item.status) ? <RevokeButton item={item} busy={busy} onRevoke={onRevoke} /> : null}
      </div>
      <div className="grid">
        {detailFields(item).map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <b>{value}</b>
          </div>
        ))}
      </div>
    </section>
  );
}
