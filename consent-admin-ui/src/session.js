const STORAGE_KEY = "consent-admin-session";

const USERS = {
  admin: {
    password: "admin123",
    displayName: "Quản trị viên",
    tenantId: "open-banking",
    actorId: "admin",
    actorType: "USER"
  }
};

export function loadSession() {
  try {
    const session = JSON.parse(sessionStorage.getItem(STORAGE_KEY) || "null");
    if (!session?.tenantId || !session?.actorId || !session?.actorType) return null;
    return session;
  } catch {
    return null;
  }
}

export function saveSession(session) {
  if (session) sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session));
  else sessionStorage.removeItem(STORAGE_KEY);
}

export function login(username, password) {
  const user = USERS[username];
  if (!user || user.password !== password) {
    throw new Error("Sai tên đăng nhập hoặc mật khẩu.");
  }
  return {
    username,
    displayName: user.displayName,
    tenantId: user.tenantId,
    actorId: user.actorId,
    actorType: user.actorType
  };
}
