import { useEffect, useState } from "react";
import {
  BrowserRouter,
  Navigate,
  Outlet,
  Route,
  Routes,
} from "react-router-dom";
import { loadStoredSession, setSessionExpiredHandler } from "./api/client.js";
import { login, logout } from "./api/auth.js";
import AdminLayout from "./layouts/AdminLayout.jsx";
import ConsentPage from "./pages/consents/ConsentPage.jsx";
import LoginScreen from "./pages/LoginScreen.jsx";
import NotFoundPage from "./pages/NotFoundPage.jsx";

export default function App() {
  const [session, setSession] = useState(loadStoredSession);

  useEffect(() => {
    setSessionExpiredHandler(() => setSession(null));
  }, []);

  async function handleLogin(username, password) {
    setSession(await login(username, password));
  }

  async function handleLogout() {
    await logout();
    setSession(null);
  }

  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/login"
          element={
            session ? (
              <Navigate to="/consents" replace />
            ) : (
              <LoginScreen onLogin={handleLogin} />
            )
          }
        />
        <Route
          element={
            session ? (
              <AdminLayout session={session} onLogout={handleLogout} />
            ) : (
              <Outlet />
            )
          }
        >
          <Route
            index
            element={<Navigate to={session ? "/consents" : "/login"} replace />}
          />
          <Route
            path="consents"
            element={
              session ? <ConsentPage /> : <Navigate to="/login" replace />
            }
          />
          <Route
            path="*"
            element={<NotFoundPage signedIn={Boolean(session)} />}
          />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
