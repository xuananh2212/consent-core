const CORE_DOWN = "Consent Core chưa sẵn sàng tại cổng 8081.";

function contextHeaders(session) {
  return {
    Accept: "application/json",
    "X-Tenant-Id": session.tenantId,
    "X-Actor-Id": session.actorId,
    "X-Actor-Type": session.actorType,
    "X-Source-System": "CONSENT_ADMIN_UI"
  };
}

async function readJson(response) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || payload.error || "Consent Core trả lỗi.");
  }
  return payload;
}

export async function searchConsents(session, { status, subjectId, page, size }) {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (status) params.set("status", status);
  if (subjectId) params.set("subjectId", subjectId);
  let response;
  try {
    response = await fetch(`/api/v1/consents?${params}`, {
      headers: contextHeaders(session)
    });
  } catch {
    throw new Error(CORE_DOWN);
  }
  return readJson(response);
}

export async function revokeConsent(session, consentId) {
  let response;
  try {
    response = await fetch(`/api/v1/consents/${consentId}/revoke`, {
      method: "POST",
      headers: {
        ...contextHeaders(session),
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        reasonCode: "CUSTOMER_REQUEST",
        reasonDetail: "Thu hồi từ màn quản lý consent"
      })
    });
  } catch {
    throw new Error(CORE_DOWN);
  }
  return readJson(response);
}
