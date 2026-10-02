const STORAGE_KEY = "consent-admin-session";

export function loadSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "null");
    if (!session?.accessToken || !session?.refreshToken || !session?.tenantId || !session?.actorId) {
      return null;
    }
    return session;
  } catch {
    return null;
  }
}

export function saveSession(session) {
  if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  else sessionStorage.removeItem(STORAGE_KEY);
}

export async function login(username, password) {
  const response = await fetch("/api/v1/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ username, password })
  });
  return readSession(response, "Sai tên đăng nhập hoặc mật khẩu.");
}

export async function refreshSession(session) {
  const response = await fetch("/api/v1/auth/refresh", {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json" },
    body: JSON.stringify({ refreshToken: session.refreshToken })
  });
  if (!response.ok) return null;
  return readSession(response);
}

export async function logout(session) {
  if (!session?.refreshToken) return;
  await fetch("/api/v1/auth/logout", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ refreshToken: session.refreshToken })
  }).catch(() => {});
}

async function readSession(response, fallbackMessage) {
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(payload.message || fallbackMessage || "Đăng nhập thất bại.");
  }
  return {
    username: payload.username,
    displayName: payload.displayName,
    tenantId: payload.tenantId,
    actorId: payload.actorId,
    actorType: payload.actorType,
    accessToken: payload.accessToken,
    refreshToken: payload.refreshToken
  };
}
