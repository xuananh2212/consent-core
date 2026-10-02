import { useCallback, useEffect, useState } from "react";
import { Button, Form, Input, Select } from "antd";
import { revokeConsent, searchConsents } from "../api/consents.js";
import Logo from "../components/Logo.jsx";

const STATUS_LABEL = {
  REGISTERED: "Đã đăng ký",
  AWAITING_AUTHORIZATION: "Chờ xác nhận",
  AUTHORIZED: "Đã đồng ý",
  REJECTED: "Từ chối",
  SUSPENDED: "Tạm dừng",
  REVOKED: "Đã thu hồi",
  EXPIRED: "Hết hạn",
  CANCELLED: "Đã hủy"
};

const REVOKABLE = new Set(["AUTHORIZED", "SUSPENDED"]);
const PAGE_SIZE = 8;

function formatWhen(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

function RevokeButton({ item, busy, onRevoke }) {
  const enabled = REVOKABLE.has(item.status) && !busy;
  const hint = enabled
    ? "Thu hồi consent"
    : "Chỉ thu hồi khi trạng thái là Đã đồng ý hoặc Tạm dừng";
  return (
    <button
      className="btn danger compact"
      type="button"
      disabled={!enabled}
      title={hint}
      onClick={(event) => {
        event.stopPropagation();
        onRevoke(item);
      }}
    >
      Revoke
    </button>
  );
}

export default function ConsentWorkspace({ session, onLogout }) {
  const [section, setSection] = useState(null);
  const [applied, setApplied] = useState({ status: "", subjectId: "" });
  const [page, setPage] = useState(0);
  const [result, setResult] = useState(null);
  const [selectedId, setSelectedId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [listError, setListError] = useState("");
  const [actionError, setActionError] = useState("");

  const load = useCallback(async () => {
    setBusy(true);
    setListError("");
    try {
      const payload = await searchConsents({
        status: applied.status,
        subjectId: applied.subjectId,
        page,
        size: PAGE_SIZE
      });
      setResult(payload);
      setSelectedId((current) => (
        current && payload.items.some((item) => item.id === current) ? current : null
      ));
    } catch (error) {
      setListError(error.message);
    } finally {
      setBusy(false);
    }
  }, [applied, page, session]);

  useEffect(() => {
    if (section === "consents") load();
  }, [section, load]);

  function applyFilter(values) {
    setPage(0);
    setApplied({ status: values.status || "", subjectId: (values.subjectId || "").trim() });
  }

  async function revoke(item) {
    if (!REVOKABLE.has(item.status)) return;
    const confirmed = window.confirm(`Thu hồi consent của ${item.subjectId || item.id}?`);
    if (!confirmed) return;
    setSelectedId(item.id);
    setBusy(true);
    setActionError("");
    try {
      await revokeConsent(item.id);
      await load();
    } catch (error) {
      setActionError(error.message);
      setBusy(false);
    }
  }

  const items = result?.items || [];
  const selected = items.find((item) => item.id === selectedId) || null;

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand-row">
          <Logo />
          <div>
            <strong>Consent Admin</strong>
            <span>Tenant {session.tenantId}</span>
          </div>
        </div>
        <div className="who">
          <span><b>{session.displayName}</b></span>
          <button className="btn ghost" type="button" onClick={onLogout}>Đăng xuất</button>
        </div>
      </header>
      <div className="workspace">
        <aside className="sider">
          <p className="sider-label">Menu</p>
          <button
            className={section === "consents" ? "nav-item active" : "nav-item"}
            type="button"
            onClick={() => setSection("consents")}
          >
            Consent
          </button>
        </aside>
        {section === "consents" ? (
          <main className="page">
            <div className="page-head">
              <div>
                <h1>Danh sách consent</h1>
                <p>{result ? `${result.totalElements} bản ghi` : "Đang tải dữ liệu từ Consent Core"}</p>
              </div>
            </div>
            <Form
              className="filter-form"
              layout="inline"
              initialValues={{ status: "", subjectId: "" }}
              onFinish={applyFilter}
            >
              <Form.Item name="status">
                <Select
                  options={[
                    { value: "", label: "Mọi trạng thái" },
                    ...Object.entries(STATUS_LABEL).map(([value, label]) => ({ value, label }))
                  ]}
                />
              </Form.Item>
              <Form.Item name="subjectId" className="filter-subject">
                <Input allowClear placeholder="Lọc theo mã chủ thể, ví dụ psu-10001" />
              </Form.Item>
              <Button type="primary" htmlType="submit">Lọc</Button>
              <Button onClick={load}>Tải lại</Button>
            </Form>
            {listError ? <div className="error">{listError}</div> : null}
            {actionError ? <div className="error">{actionError}</div> : null}
            <div className="table-wrap">
              {busy && !result ? <div className="loading">Đang tải danh sách…</div> : <ConsentTable items={items} selectedId={selectedId} busy={busy} onSelect={setSelectedId} onRevoke={revoke} />}
            </div>
            <Pager result={result} onPage={setPage} />
            {selected ? <Detail item={selected} busy={busy} onRevoke={revoke} /> : null}
          </main>
        ) : (
          <main className="page welcome">
            <h1>Consent Admin</h1>
            <p>Chọn Consent trên menu bên trái để xem danh sách.</p>
          </main>
        )}
      </div>
    </div>
  );
}

function ConsentTable({ items, selectedId, busy, onSelect, onRevoke }) {
  if (!items.length) return <div className="empty">Không có consent phù hợp bộ lọc.</div>;
  return (
    <table>
      <thead>
        <tr>
          <th>Chủ thể</th>
          <th>Loại</th>
          <th>Ứng dụng</th>
          <th>Mục đích</th>
          <th>Trạng thái</th>
          <th>Hiệu lực đến</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        {items.map((item) => (
          <tr
            key={item.id}
            className={item.id === selectedId ? "active" : undefined}
            onClick={() => onSelect(item.id)}
          >
            <td>
              <b>{item.subjectId || "Chưa gắn"}</b>
              <div className="mono">{item.id}</div>
            </td>
            <td>{item.consentType}</td>
            <td>{item.clientId}</td>
            <td>{item.purpose || "—"}</td>
            <td><span className={`pill ${item.status}`}>{STATUS_LABEL[item.status] || item.status}</span></td>
            <td>{formatWhen(item.validUntil)}</td>
            <td className="actions">
              <RevokeButton item={item} busy={busy} onRevoke={onRevoke} />
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

function Pager({ result, onPage }) {
  if (!result || result.totalPages <= 1) return null;
  const page = result.page + 1;
  return (
    <div className="pager">
      <span>Trang {page} / {result.totalPages}</span>
      <span>
        <button className="btn ghost" type="button" disabled={result.page <= 0} onClick={() => onPage(result.page - 1)}>
          Trước
        </button>
        <button className="btn ghost" type="button" disabled={page >= result.totalPages} onClick={() => onPage(result.page + 1)}>
          Sau
        </button>
      </span>
    </div>
  );
}

function Detail({ item, busy, onRevoke }) {
  const fields = [
    ["Mã consent", item.id],
    ["Trạng thái", STATUS_LABEL[item.status] || item.status],
    ["Chủ thể", item.subjectId || "Chưa gắn"],
    ["Ứng dụng", item.clientId],
    ["Hệ thống nguồn", item.sourceSystem],
    ["Kênh", item.acquisitionChannel],
    ["Cách ghi nhận", item.captureMethod],
    ["Chính sách bằng chứng", item.evidencePolicy],
    ["Trạng thái bằng chứng", item.evidenceStatus],
    ["Hiệu lực từ", formatWhen(item.validFrom)],
    ["Hiệu lực đến", formatWhen(item.validUntil)],
    ["Cập nhật", formatWhen(item.updatedAt)],
    ["Mã yêu cầu ngoài", item.externalRequestId || "—"],
    ["Tham chiếu ủy quyền", item.authorizationReference || "—"],
    ["Người tạo", item.createdBy]
  ];
  return (
    <section className="detail">
      <div className="detail-head">
        <h2>{item.purpose || item.consentType}</h2>
        {REVOKABLE.has(item.status) ? <RevokeButton item={item} busy={busy} onRevoke={onRevoke} /> : null}
      </div>
      <div className="grid">
        {fields.map(([label, value]) => (
          <div key={label}>
            <span>{label}</span>
            <b>{value}</b>
          </div>
        ))}
      </div>
    </section>
  );
}
