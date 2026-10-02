import axios from "axios";
import Cookies from "js-cookie";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || "";
const CORE_DOWN = API_BASE_URL
  ? `Consent Core chưa sẵn sàng tại ${API_BASE_URL}.`
  : "Consent Core chưa sẵn sàng.";
const ACCESS_COOKIE = "accessToken";
const REFRESH_COOKIE = "refreshToken";
const PROFILE_COOKIE = "cms_profile";
const REFRESH_MAX_AGE = 60 * 60 * 8;

let accessToken = Cookies.get(ACCESS_COOKIE) || null;
let refreshToken = Cookies.get(REFRESH_COOKIE) || null;
let onSessionExpired = () => {};

const http = axios.create({
  baseURL: API_BASE_URL,
  headers: { Accept: "application/json" },
  withCredentials: true
});

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

export function currentRefreshToken() {
  return refreshToken || Cookies.get(REFRESH_COOKIE) || null;
}

export function loadStoredSession() {
  accessToken = Cookies.get(ACCESS_COOKIE) || null;
  refreshToken = Cookies.get(REFRESH_COOKIE) || null;
  if (!accessToken && !refreshToken) return null;
  return readProfile();
}

export function setTokens(nextAccessToken, nextRefreshToken, expiresIn) {
  accessToken = nextAccessToken || null;
  if (nextRefreshToken) refreshToken = nextRefreshToken;
  if (accessToken) writeCookie(ACCESS_COOKIE, accessToken, expiresIn || 900);
  if (nextRefreshToken) writeCookie(REFRESH_COOKIE, nextRefreshToken, REFRESH_MAX_AGE);
}

export function saveProfile(session) {
  writeCookie(PROFILE_COOKIE, JSON.stringify({
    username: session.username,
    displayName: session.displayName,
    tenantId: session.tenantId,
    actorId: session.actorId,
    actorType: session.actorType,
    sourceSystem: session.sourceSystem
  }), REFRESH_MAX_AGE);
}

export function clearTokens() {
  accessToken = null;
  refreshToken = null;
  deleteCookie(ACCESS_COOKIE);
  deleteCookie(REFRESH_COOKIE);
  deleteCookie(PROFILE_COOKIE);
  deleteCookie("access_token");
  deleteCookie("refresh_token");
}

http.interceptors.request.use((config) => {
  const token = accessToken || Cookies.get(ACCESS_COOKIE);
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error) => {
    if (!error.config) return Promise.reject(error);

    const original = error.config;
    const url = original.url || "";
    const canRefresh = error.response?.status === 401 && !original._retry && !url.includes("/api/v1/auth/");
    if (!canRefresh) return Promise.reject(normalize(error));

    const currentRefresh = refreshToken || Cookies.get(REFRESH_COOKIE);
    if (!currentRefresh) {
      clearTokens();
      onSessionExpired();
      return Promise.reject(normalize(error));
    }

    original._retry = true;
    try {
      const { data } = await http.post("/api/v1/auth/refresh", { refreshToken: currentRefresh });
      setTokens(data.accessToken, data.refreshToken, data.expiresIn);
      saveProfile(data);
      original.headers.Authorization = `Bearer ${data.accessToken}`;
      return http(original);
    } catch (refreshError) {
      clearTokens();
      onSessionExpired();
      return Promise.reject(refreshError);
    }
  }
);

function readProfile() {
  const raw = Cookies.get(PROFILE_COOKIE);
  if (!raw) return null;
  try {
    const profile = JSON.parse(raw);
    if (!profile?.tenantId || !profile?.actorId || !profile?.actorType) return null;
    return profile;
  } catch {
    return null;
  }
}

function writeCookie(name, value, maxAge) {
  Cookies.set(name, value, { path: "/", sameSite: "Lax", expires: maxAge / 86400 });
}

function deleteCookie(name) {
  Cookies.remove(name, { path: "/" });
}

function normalize(error) {
  if (!error.response) return new Error(CORE_DOWN);
  const payload = error.response.data || {};
  return new Error(payload.message || payload.error || "Consent Core trả lỗi.");
}

export default http;
