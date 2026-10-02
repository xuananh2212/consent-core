import { useState } from "react";
import Logo from "./Logo.jsx";

export default function LoginScreen({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event) {
    event.preventDefault();
    setError("");
    setBusy(true);
    try {
      await onLogin(username.trim(), password);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <section className="login">
      <div className="login-brand">
        <div>
          <Logo />
          <h1>Quản lý consent cho kênh Open Banking</h1>
          <p>Đăng nhập để xem các yêu cầu đồng ý đã ghi trên Consent Core.</p>
        </div>
        <div className="brand-foot">Consent Admin</div>
      </div>
      <div className="login-panel">
        <form className="card" onSubmit={submit}>
          <h2>Đăng nhập</h2>
          <p className="sub">Dùng tài khoản vận hành để vào danh sách consent.</p>
          <label htmlFor="username">Tên đăng nhập</label>
          <input
            id="username"
            name="username"
            autoComplete="username"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
            required
          />
          <label htmlFor="password">Mật khẩu</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            required
          />
          {error ? <div className="error">{error}</div> : null}
          <button className="btn primary block" type="submit" disabled={busy}>
            Đăng nhập
          </button>
          <div className="hint">
            Tài khoản demo: <b>admin</b> / <b>admin123</b>
          </div>
        </form>
      </div>
    </section>
  );
}
