import { useState } from "react";
import ConsentWorkspace from "./ConsentWorkspace.jsx";
import LoginScreen from "./LoginScreen.jsx";
import { loadSession, login, logout, saveSession } from "./session.js";

export default function App() {
  const [session, setSession] = useState(loadSession);

  async function handleLogin(username, password) {
    const next = await login(username, password);
    saveSession(next);
    setSession(next);
  }

  async function handleLogout() {
    await logout(session);
    saveSession(null);
    setSession(null);
  }

  if (!session) return <LoginScreen onLogin={handleLogin} />;
  return <ConsentWorkspace session={session} onLogout={handleLogout} />;
}
