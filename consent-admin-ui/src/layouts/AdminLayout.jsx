import { NavLink, Outlet } from "react-router-dom";
import Logo from "../components/Logo.jsx";

const MENU = [
  { to: "/consents", label: "Consent" }
];

export default function AdminLayout({ session, onLogout }) {
  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand-row">
          <Logo />
          <div>
            <strong>Consent Admin</strong>
            <span>Tenant {session.tenantId}</span>
          </div>
        </div>
        <div className="who">
          <span><b>{session.displayName}</b></span>
          <button className="btn ghost" type="button" onClick={onLogout}>Đăng xuất</button>
        </div>
      </header>
      <div className="workspace">
        <aside className="sider">
          <p className="sider-label">Menu</p>
          {MENU.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => (isActive ? "nav-item active" : "nav-item")}
            >
              {item.label}
            </NavLink>
          ))}
        </aside>
        <Outlet />
      </div>
    </div>
  );
}
