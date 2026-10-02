import axios from "axios";

const CORE_DOWN = "Consent Core chưa sẵn sàng tại cổng 8081.";

let accessToken = null;
let refreshToken = null;
let onSessionExpired = () => {};

const http = axios.create({
  headers: { Accept: "application/json" },
  withCredentials: true
});

export function setTokens(nextAccessToken, nextRefreshToken) {
  accessToken = nextAccessToken || null;
  if (nextRefreshToken) refreshToken = nextRefreshToken;
}

export function clearTokens() {
  accessToken = null;
  refreshToken = null;
}

export function currentRefreshToken() {
  return refreshToken;
}

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

http.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!error.config) return Promise.reject(error);

    const original = error.config;
    const url = original.url || "";
    if (error.response?.status === 401 && !original._retry && !url.includes("/api/v1/auth/")) {
      original._retry = true;
      try {
        const { data } = await http.post("/api/v1/auth/refresh", { refreshToken });
        setTokens(data.accessToken, data.refreshToken);
        original.headers.Authorization = `Bearer ${data.accessToken}`;
        return http(original);
      } catch (refreshError) {
        clearTokens();
        onSessionExpired();
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(normalize(error));
  }
);

function normalize(error) {
  if (!error.response) return new Error(CORE_DOWN);
  const payload = error.response.data || {};
  return new Error(payload.message || payload.error || "Consent Core trả lỗi.");
}

export default http;
