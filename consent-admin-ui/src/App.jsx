import { useEffect, useState } from "react";
import { setSessionExpiredHandler } from "./api/client.js";
import { login, logout, restoreSession } from "./api/auth.js";
import ConsentWorkspace from "./pages/ConsentWorkspace.jsx";
import LoginScreen from "./pages/LoginScreen.jsx";

export default function App() {
  const [session, setSession] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    setSessionExpiredHandler(() => setSession(null));
    let active = true;
    restoreSession()
      .then((next) => {
        if (active) setSession(next);
      })
      .catch(() => {
        if (active) setSession(null);
      })
      .finally(() => {
        if (active) setReady(true);
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleLogin(username, password) {
    setSession(await login(username, password));
  }

  async function handleLogout() {
    await logout();
    setSession(null);
  }

  if (!ready) return null;
  if (!session) return <LoginScreen onLogin={handleLogin} />;
  return <ConsentWorkspace session={session} onLogout={handleLogout} />;
}
