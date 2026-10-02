import http, { clearTokens, currentRefreshToken, setTokens } from "./client.js";

function remember(data) {
  setTokens(data.accessToken, data.refreshToken);
  return data;
}

export async function login(username, password) {
  const { data } = await http.post("/api/v1/auth/login", { username, password });
  return remember(data);
}

export async function restoreSession() {
  try {
    const { data } = await http.post("/api/v1/auth/refresh");
    return remember(data);
  } catch (error) {
    clearTokens();
    throw error;
  }
}

export async function logout() {
  try {
    await http.post("/api/v1/auth/logout", { refreshToken: currentRefreshToken() });
  } finally {
    clearTokens();
  }
}
