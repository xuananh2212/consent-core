import http from "./client.js";

export async function searchConsents({ status, subjectId, page, size }) {
  const { data } = await http.get("/api/v1/consents", {
    params: {
      page,
      size,
      ...(status ? { status } : {}),
      ...(subjectId ? { subjectId } : {})
    }
  });
  return data;
}

export async function revokeConsent(consentId) {
  const { data } = await http.post(`/api/v1/consents/${consentId}/revoke`, {
    reasonCode: "CUSTOMER_REQUEST",
    reasonDetail: "Thu hồi từ màn quản lý consent"
  });
  return data;
}
