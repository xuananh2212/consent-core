import { refreshSession, saveSession } from "./session.js";

const CORE_DOWN = "Consent Core chưa sẵn sàng tại cổng 8081.";

function withAuth(session, options) {
  const headers = new Headers(options.headers || {});
  headers.set("Accept", "application/json");
  headers.set("Authorization", `Bearer ${session.accessToken}`);
  return { ...options, headers };
}

async function readJson(response) {
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || payload.error || "Consent Core trả lỗi.");
  }
  return payload;
}

async function authorizedFetch(session, url, options) {
  let response;
  try {
    response = await fetch(url, withAuth(session, options));
  } catch {
    throw new Error(CORE_DOWN);
  }
  if (response.status !== 401) return response;

  const next = await refreshSession(session);
  if (!next) {
    saveSession(null);
    window.location.assign("/");
    throw new Error("Phiên đăng nhập đã hết hạn.");
  }
  Object.assign(session, next);
  saveSession(session);
  try {
    return await fetch(url, withAuth(session, options));
  } catch {
    throw new Error(CORE_DOWN);
  }
}

export async function searchConsents(session, { status, subjectId, page, size }) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  if (subjectId) params.set("subjectId", subjectId);
  const response = await authorizedFetch(session, `/api/v1/consents?${params}`, {});
  return readJson(response);
}

export async function revokeConsent(session, consentId) {
  const response = await authorizedFetch(session, `/api/v1/consents/${consentId}/revoke`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      reasonCode: "CUSTOMER_REQUEST",
      reasonDetail: "Thu hồi từ màn quản lý consent"
    })
  });
  return readJson(response);
}
