import http, {
  clearTokens,
  currentRefreshToken,
  saveProfile,
  setTokens,
} from "./client.js";

function remember(data) {
  console.log(data);
  setTokens(data.accessToken, data.refreshToken, data.expiresIn);
  saveProfile(data);
  return data;
}

export async function login(username, password) {
  const { data } = await http.post("/api/v1/auth/login", {
    username,
    password,
  });
  return remember(data);
}

export async function logout() {
  try {
    await http.post("/api/v1/auth/logout", {
      refreshToken: currentRefreshToken(),
    });
  } finally {
    clearTokens();
  }
}
