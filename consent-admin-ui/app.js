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

const state = {
  session: loadSession(),
  username: "",
  password: "",
  error: "",
  busy: false,
  status: "",
  subjectId: "",
  page: 0,
  size: 8,
  result: null,
  selectedId: null,
  listError: ""
};

const app = document.getElementById("app");

function loadSession() {
  try {
    return JSON.parse(sessionStorage.getItem("consent-admin-session") || "null");
  } catch {
    return null;
  }
}

function saveSession(session) {
  state.session = session;
  if (session) sessionStorage.setItem("consent-admin-session", JSON.stringify(session));
  else sessionStorage.removeItem("consent-admin-session");
}

function esc(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function formatWhen(value) {
  if (!value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short"
  }).format(date);
}

function render() {
  app.innerHTML = state.session ? workspace() : login();
  bind();
}

function login() {
  return `
    <section class="login">
      <div class="login-brand">
        <div>
          <div class="mark">SHB</div>
          <h1>Quản lý consent cho kênh Open Banking</h1>
          <p>Đăng nhập để xem các yêu cầu đồng ý đã ghi trên Consent Core.</p>
        </div>
        <div class="brand-foot">Consent Admin</div>
      </div>
      <div class="login-panel">
        <form class="card" id="login-form">
          <h2>Đăng nhập</h2>
          <p class="sub">Dùng tài khoản vận hành để vào danh sách consent.</p>
          <label for="username">Tên đăng nhập</label>
          <input id="username" name="username" autocomplete="username" value="${esc(state.username)}" required>
          <label for="password">Mật khẩu</label>
          <input id="password" name="password" type="password" autocomplete="current-password" required>
          ${state.error ? `<div class="error">${esc(state.error)}</div>` : ""}
          <button class="btn primary block" type="submit" ${state.busy ? "disabled" : ""}>Đăng nhập</button>
          <div class="hint">Tài khoản demo: <b>admin</b> / <b>admin123</b></div>
        </form>
      </div>
    </section>`;
}

function workspace() {
  const items = state.result?.items || [];
  const selected = items.find((item) => item.id === state.selectedId) || null;
  return `
    <div class="shell">
      <header class="topbar">
        <div class="brand-row">
          <div class="mark">SHB</div>
          <div><strong>Consent Admin</strong><span>Tenant ${esc(state.session.tenantId)}</span></div>
        </div>
        <div class="who">
          <span><b>${esc(state.session.displayName)}</b></span>
          <button class="btn ghost" type="button" id="logout">Đăng xuất</button>
        </div>
      </header>
      <main class="page">
        <div class="page-head">
          <div>
            <h1>Danh sách consent</h1>
            <p>${state.result ? `${state.result.totalElements} bản ghi` : "Đang tải dữ liệu từ Consent Core"}</p>
          </div>
        </div>
        <form class="toolbar" id="filter-form">
          <select id="status" name="status">
            <option value="">Mọi trạng thái</option>
            ${Object.entries(STATUS_LABEL).map(([value, label]) => `
              <option value="${value}" ${state.status === value ? "selected" : ""}>${esc(label)}</option>`).join("")}
          </select>
          <input id="subjectId" name="subjectId" placeholder="Lọc theo mã chủ thể, ví dụ psu-10001" value="${esc(state.subjectId)}">
          <button class="btn primary" type="submit">Lọc</button>
          <button class="btn ghost" type="button" id="refresh">Tải lại</button>
        </form>
        ${state.listError ? `<div class="error">${esc(state.listError)}</div>` : ""}
        <div class="table-wrap">
          ${state.busy && !state.result ? `<div class="loading">Đang tải danh sách…</div>` : table(items)}
        </div>
        ${pager()}
        ${selected ? detail(selected) : ""}
      </main>
    </div>`;
}

function table(items) {
  if (!items.length) {
    return `<div class="empty">Không có consent phù hợp bộ lọc.</div>`;
  }
  return `
    <table>
      <thead>
        <tr>
          <th>Chủ thể</th>
          <th>Loại</th>
          <th>Ứng dụng</th>
          <th>Mục đích</th>
          <th>Trạng thái</th>
          <th>Hiệu lực đến</th>
        </tr>
      </thead>
      <tbody>
        ${items.map((item) => `
          <tr data-id="${esc(item.id)}" class="${item.id === state.selectedId ? "active" : ""}">
            <td><b>${esc(item.subjectId || "Chưa gắn")}</b><div class="mono">${esc(item.id)}</div></td>
            <td>${esc(item.consentType)}</td>
            <td>${esc(item.clientId)}</td>
            <td>${esc(item.purpose || "—")}</td>
            <td><span class="pill ${esc(item.status)}">${esc(STATUS_LABEL[item.status] || item.status)}</span></td>
            <td>${esc(formatWhen(item.validUntil))}</td>
          </tr>`).join("")}
      </tbody>
    </table>`;
}

function pager() {
  if (!state.result || state.result.totalPages <= 1) return "";
  const page = state.result.page + 1;
  return `
    <div class="pager">
      <span>Trang ${page} / ${state.result.totalPages}</span>
      <span>
        <button class="btn ghost" type="button" id="prev" ${state.result.page <= 0 ? "disabled" : ""}>Trước</button>
        <button class="btn ghost" type="button" id="next" ${page >= state.result.totalPages ? "disabled" : ""}>Sau</button>
      </span>
    </div>`;
}

function detail(item) {
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
  return `
    <section class="detail">
      <h2>${esc(item.purpose || item.consentType)}</h2>
      <div class="grid">
        ${fields.map(([label, value]) => `<div><span>${esc(label)}</span><b>${esc(value)}</b></div>`).join("")}
      </div>
    </section>`;
}

function bind() {
  const form = document.getElementById("login-form");
  if (form) form.onsubmit = submitLogin;
  const logout = document.getElementById("logout");
  if (logout) logout.onclick = () => {
    saveSession(null);
    state.result = null;
    state.selectedId = null;
    state.error = "";
    render();
  };
  const filter = document.getElementById("filter-form");
  if (filter) filter.onsubmit = (event) => {
    event.preventDefault();
    state.status = document.getElementById("status").value;
    state.subjectId = document.getElementById("subjectId").value.trim();
    state.page = 0;
    loadConsents();
  };
  const refresh = document.getElementById("refresh");
  if (refresh) refresh.onclick = () => loadConsents();
  document.querySelectorAll("tbody tr[data-id]").forEach((row) => {
    row.onclick = () => {
      state.selectedId = row.dataset.id;
      render();
    };
  });
  const prev = document.getElementById("prev");
  const next = document.getElementById("next");
  if (prev) prev.onclick = () => { state.page -= 1; loadConsents(); };
  if (next) next.onclick = () => { state.page += 1; loadConsents(); };
}

async function submitLogin(event) {
  event.preventDefault();
  state.username = document.getElementById("username").value.trim();
  state.password = document.getElementById("password").value;
  state.error = "";
  state.busy = true;
  render();
  try {
    const response = await fetch("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: state.username, password: state.password })
    });
    const payload = await response.json();
    if (!response.ok) throw new Error(payload.error || "Đăng nhập không thành công.");
    state.password = "";
    saveSession(payload);
    state.busy = false;
    render();
    await loadConsents();
  } catch (error) {
    state.busy = false;
    state.error = error.message;
    render();
  }
}

async function loadConsents() {
  state.busy = true;
  state.listError = "";
  render();
  const params = new URLSearchParams({ page: String(state.page), size: String(state.size) });
  if (state.status) params.set("status", state.status);
  if (state.subjectId) params.set("subjectId", state.subjectId);
  try {
    const response = await fetch(`/api/v1/consents?${params}`, {
      headers: { Authorization: `Bearer ${state.session.token}` }
    });
    const payload = await response.json();
    if (response.status === 401) {
      saveSession(null);
      state.error = payload.error || "Phiên đăng nhập đã hết hạn.";
      state.busy = false;
      render();
      return;
    }
    if (!response.ok) throw new Error(payload.message || payload.error || "Không tải được danh sách consent.");
    state.result = payload;
    if (state.selectedId && !payload.items.some((item) => item.id === state.selectedId)) {
      state.selectedId = null;
    }
  } catch (error) {
    state.listError = error.message;
  } finally {
    state.busy = false;
    render();
  }
}

render();
if (state.session) loadConsents();
