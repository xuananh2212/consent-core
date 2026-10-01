const SCENARIOS = [
  { id: "standard", title: "Đồng ý sau khi chọn tài khoản", detail: "Login, chọn tài khoản, rồi Đồng ý hoặc Từ chối" },
  { id: "denied", title: "Không đủ điều kiện", detail: "Sau login chỉ có trang lỗi, không có nút đồng ý" },
  { id: "already", title: "Đã đồng ý từ trước", detail: "Login xong quay về ứng dụng, không hiện lại màn đồng ý" }
];

const state = {
  scenario: "standard",
  screen: "tpp-start",
  username: "",
  password: "",
  error: "",
  selected: new Set(),
  accounts: [],
  presentation: null,
  record: null,
  decision: null,
  busy: false
};

const screenEl = document.getElementById("screen");
const presenterEl = document.getElementById("presenter");

function esc(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

function render() {
  presenterEl.innerHTML = presenter();
  screenEl.innerHTML = screens[state.screen]();
  bind();
}

function presenter() {
  const hint = {
    "tpp-start": "Khách đang ở ứng dụng bên thứ ba. Nút này chỉ xin chuyển sang ngân hàng, chưa ghi đồng ý.",
    "bank-login": "Đây là màn đăng nhập của ngân hàng. Bấm Đăng nhập không tạo consent.",
    "bank-wait": "Ngân hàng chuẩn bị nội dung trước khi mở màn đồng ý. Nếu bị từ chối điều kiện, khách không thấy màn đó.",
    "bank-denied": "Prepare trả DENIED. Không có Đồng ý hay Từ chối. Consent vẫn ở trạng thái đã đăng ký.",
    "bank-select": "Nút Tiếp tục gửi tài khoản đã chọn lên backend. Consent vẫn REGISTERED cho đến khi backend chốt nội dung.",
    "bank-consent": "Đồng ý gọi API decision và backend ghi AUTHORIZED. Từ chối ghi REJECTED, đúng revision và content hash đang hiện.",
    "bank-resume": "Consent đã có hiệu lực. Khách chỉ xác thực lại để lấy mã mới, không bấm đồng ý lần nữa.",
    "tpp-result": "Khách đã trở về ứng dụng bên thứ ba. Token do máy chủ ủy quyền cấp, không do màn hình này cấp."
  }[state.screen];

  return `
    <h1>Luồng đồng ý cho khách hàng</h1>
    <p class="lead">Màn hình gọi demo backend. Backend tạo consent, chuẩn bị nội dung, rồi ghi Đồng ý hoặc Từ chối.</p>
    ${state.record ? `<div class="note"><strong>Consent trên backend</strong>${esc(state.record.consentId)}<br>${esc(state.record.status)}</div>` : ""}
    <div class="scenarios">
      ${SCENARIOS.map((item) => `
        <button type="button" data-scenario="${item.id}" class="${state.scenario === item.id ? "active" : ""}">
          ${esc(item.title)}
          <small>${esc(item.detail)}</small>
        </button>`).join("")}
    </div>
    <div class="note"><strong>Bước đang xem</strong>${esc(hint)}</div>
    <button class="ghost" type="button" id="restart">Làm lại từ ứng dụng</button>
  `;
}

const screens = {
  "tpp-start"() {
    return `
      <section class="splash">
        <div class="brand"><div class="mark tpp">FC</div><div>FinConnect<div class="who" style="color:#94a3b8">Ứng dụng bên thứ ba</div></div></div>
        <div class="center-copy">
          <h2>Kết nối tài khoản SHB</h2>
          <p>FinConnect cần xem số dư và giao dịch để hiển thị tài chính của bạn. Bạn sẽ đăng nhập tại SHB và tự quyết định có chia sẻ hay không.</p>
        </div>
        ${state.error ? `<div class="field-error">${esc(state.error)}</div>` : ""}
        <div class="actions">
          <button class="btn tpp" type="button" id="start" ${state.busy ? "disabled" : ""}>Tiếp tục với SHB</button>
        </div>
      </section>`;
  },
  "bank-login"() {
    return bankShell(1, `
      <h2>Đăng nhập SHB</h2>
      <p>Dùng tài khoản ngân hàng để xác nhận bạn là chủ tài khoản.</p>
      <form id="login-form">
        <label for="username">Tên đăng nhập</label>
        <input id="username" name="username" autocomplete="username" value="${esc(state.username)}">
        <label for="password">Mật khẩu</label>
        <input id="password" name="password" type="password" autocomplete="current-password" value="${esc(state.password)}">
        ${state.error ? `<div class="field-error">${esc(state.error)}</div>` : ""}
        <div class="actions">
          <button class="btn primary" type="submit">Đăng nhập</button>
          <button class="btn ghost-dark" type="button" id="back-tpp">Quay lại FinConnect</button>
        </div>
      </form>`);
  },
  "bank-wait"() {
    return bankShell(2, `
      <div class="center-copy">
        <div class="badge wait">Đang chuẩn bị</div>
        <h2>SHB đang kiểm tra yêu cầu</h2>
        <p>Nội dung đồng ý chỉ hiện sau khi ngân hàng chuẩn bị xong.</p>
      </div>`);
  },
  "bank-denied"() {
    return bankShell(2, `
      <div class="center-copy">
        <div class="badge no">Không tiếp tục được</div>
        <h2>SHB chưa thể cho phép yêu cầu này</h2>
        <p>Bạn không cần chọn tài khoản hay bấm đồng ý. Hãy quay lại ứng dụng.</p>
        <div class="actions">
          <button class="btn primary" type="button" id="finish-denied">Quay lại FinConnect</button>
        </div>
      </div>`);
  },
  "bank-select"() {
    return bankShell(2, `
      <h2>Chọn tài khoản chia sẻ</h2>
      <p>FinConnect chỉ nhận các tài khoản bạn chọn ở bước này.</p>
      <div class="choices">
        ${state.accounts.map((account) => `
          <button type="button" class="account ${state.selected.has(account.id) ? "selected" : ""}" data-account="${account.id}">
            <div class="check">${state.selected.has(account.id) ? "✓" : ""}</div>
            <div>
              <b>${esc(account.name)}</b>
              <small>${esc(account.number)} · ${esc(account.balance)}</small>
            </div>
          </button>`).join("")}
      </div>
      ${state.error ? `<div class="field-error">${esc(state.error)}</div>` : ""}
      <div class="actions">
        <button class="btn primary" type="button" id="continue-select">Tiếp tục</button>
      </div>`);
  },
  "bank-consent"() {
    const presentation = state.presentation || { title: "", summary: "", permissions: [], accounts: [], clientName: "" };
    return bankShell(3, `
      <h2>${esc(presentation.title)}</h2>
      <p>${esc(presentation.summary)}</p>
      ${presentation.permissions.map((item) => `<div class="perm"><i></i><div>${esc(item)}</div></div>`).join("")}
      <div class="sheet">
        ${presentation.accounts.map((account) => `<div class="row"><span>${esc(account.name)}</span><b>${esc(account.number)}</b></div>`).join("")}
        <div class="row"><span>Ứng dụng</span><b>${esc(presentation.clientName)}</b></div>
        <div class="row"><span>Bản nội dung</span><b>${esc(state.record?.revisionNo || "")}</b></div>
      </div>
      <div class="actions">
        <button class="btn primary" type="button" id="approve">Đồng ý</button>
        <button class="btn danger" type="button" id="reject">Từ chối</button>
      </div>`);
  },
  "bank-resume"() {
    return bankShell(3, `
      <div class="center-copy">
        <div class="badge ok">Đã xác thực</div>
        <h2>Bạn đã đồng ý yêu cầu này trước đó</h2>
        <p>SHB không hỏi lại. Bạn được đưa về FinConnect để tiếp tục.</p>
        <div class="actions">
          <button class="btn primary" type="button" id="finish-already">Tiếp tục về FinConnect</button>
        </div>
      </div>`);
  },
  "tpp-result"() {
    const copy = {
      approved: ["Đã kết nối SHB", "Backend đã ghi consent AUTHORIZED cho đúng bản nội dung vừa hiện."],
      rejected: ["Chưa kết nối SHB", "Backend đã ghi consent REJECTED. FinConnect không nhận dữ liệu tài khoản."],
      denied: ["SHB không tiếp tục yêu cầu", "Backend giữ consent REGISTERED. Không có quyết định đồng ý hay từ chối."],
      already: ["Dùng đồng ý đã cấp", "Backend trả consent AUTHORIZED đã có. Lần đăng nhập này không tạo quyết định mới."]
    }[state.decision];
    return `
      <section class="splash">
        <div class="brand"><div class="mark tpp">FC</div><div>FinConnect<div class="who" style="color:#94a3b8">Đã quay lại ứng dụng</div></div></div>
        <div class="center-copy">
          <h2>${esc(copy[0])}</h2>
          <p>${esc(copy[1])}</p>
          ${state.record ? `<p>Mã consent ${esc(state.record.consentId)} · ${esc(state.record.status)}</p>` : ""}
        </div>
        <div class="actions">
          <button class="btn tpp" type="button" id="restart-inline">Xem lại luồng</button>
        </div>
      </section>`;
  }
};

function bankShell(step, body) {
  return `
    <header class="bank-top">
      <div class="brand"><div class="mark bank">SHB</div><div>SHB<div class="who">Ngân hàng</div></div></div>
    </header>
    <div class="steps">${[1, 2, 3].map((n) => `<span class="${n <= step ? "on" : ""}"></span>`).join("")}</div>
    <div class="bank-body">${body}</div>`;
}

function bind() {
  document.querySelectorAll("[data-scenario]").forEach((button) => {
    button.onclick = () => {
      state.scenario = button.dataset.scenario;
      resetJourney();
    };
  });
  const restart = document.getElementById("restart");
  if (restart) restart.onclick = resetJourney;
  const restartInline = document.getElementById("restart-inline");
  if (restartInline) restartInline.onclick = resetJourney;

  const start = document.getElementById("start");
  if (start) start.onclick = startJourney;

  const back = document.getElementById("back-tpp");
  if (back) back.onclick = () => go("tpp-start");

  const form = document.getElementById("login-form");
  if (form) form.onsubmit = submitLogin;

  document.querySelectorAll("[data-account]").forEach((button) => {
    button.onclick = () => {
      const id = button.dataset.account;
      if (state.selected.has(id)) state.selected.delete(id);
      else state.selected.add(id);
      state.error = "";
      render();
    };
  });

  const continueSelect = document.getElementById("continue-select");
  if (continueSelect) continueSelect.onclick = submitSelection;

  const approve = document.getElementById("approve");
  if (approve) approve.onclick = () => submitDecision("APPROVE");
  const reject = document.getElementById("reject");
  if (reject) reject.onclick = () => submitDecision("REJECT");
  const finishDenied = document.getElementById("finish-denied");
  if (finishDenied) finishDenied.onclick = () => finish("denied");
  const finishAlready = document.getElementById("finish-already");
  if (finishAlready) finishAlready.onclick = () => finish("already");
}

async function api(path, body) {
  const response = await fetch(path, {
    method: body ? "POST" : "GET",
    headers: body ? { "Content-Type": "application/json" } : {},
    body: body ? JSON.stringify(body) : undefined
  });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || "Backend từ chối yêu cầu.");
  return payload;
}

async function startJourney() {
  state.busy = true;
  state.error = "";
  render();
  try {
    state.record = await api("/api/journeys", { scenario: state.scenario });
    state.busy = false;
    go("bank-login");
  } catch (error) {
    state.busy = false;
    state.error = error.message;
    render();
  }
}

async function submitLogin(event) {
  event.preventDefault();
  state.username = document.getElementById("username").value.trim();
  state.password = document.getElementById("password").value;
  state.error = "";
  go("bank-wait");
  try {
    const result = await api(`/api/journeys/${state.record.journeyId}/authenticate`, {
      username: state.username,
      password: state.password
    });
    state.record = result;
    if (result.preparationStatus === "DENIED") go("bank-denied");
    else if (result.preparationStatus === "ALREADY_AUTHORIZED") go("bank-resume");
    else {
      state.accounts = result.accounts;
      state.selected = new Set([result.accounts[0].id]);
      go("bank-select");
    }
  } catch (error) {
    state.error = error.message;
    go("bank-login");
  }
}

async function submitSelection() {
  if (state.selected.size === 0) {
    state.error = "Chọn ít nhất một tài khoản.";
    render();
    return;
  }
  state.error = "";
  go("bank-wait");
  try {
    const result = await api(`/api/journeys/${state.record.journeyId}/selection`, {
      accountIds: [...state.selected],
      selectionContextHash: state.record.selectionContextHash
    });
    state.record = result;
    state.presentation = result.presentation;
    go("bank-consent");
  } catch (error) {
    state.error = error.message;
    go("bank-select");
  }
}

async function submitDecision(decision) {
  go("bank-wait");
  try {
    const result = await api(`/api/journeys/${state.record.journeyId}/decision`, {
      decision,
      revisionNo: state.record.revisionNo,
      contentHash: state.record.contentHash
    });
    state.record = result;
    finish(result.decision);
  } catch (error) {
    state.error = error.message;
    go("bank-consent");
  }
}

function finish(decision) {
  state.decision = decision;
  go("tpp-result");
}

function go(screen) {
  state.screen = screen;
  render();
}

function resetJourney() {
  state.screen = "tpp-start";
  state.username = "";
  state.password = "";
  state.error = "";
  state.selected = new Set();
  state.accounts = [];
  state.presentation = null;
  state.record = null;
  state.decision = null;
  state.busy = false;
  render();
}

render();
