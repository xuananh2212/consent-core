import { useState } from "react";
import ConsentWorkspace from "./ConsentWorkspace.jsx";
import LoginScreen from "./LoginScreen.jsx";
import { loadSession, login, saveSession } from "./session.js";

export default function App() {
  const [session, setSession] = useState(loadSession);

  function handleLogin(username, password) {
    const next = login(username, password);
    saveSession(next);
    setSession(next);
  }

  function handleLogout() {
    saveSession(null);
    setSession(null);
  }

  if (!session) return <LoginScreen onLogin={handleLogin} />;
  return <ConsentWorkspace session={session} onLogout={handleLogout} />;
}
