import { Link, useLocation } from "react-router-dom";
import Logo from "../components/Logo.jsx";

export default function NotFoundPage({ signedIn }) {
  const { pathname } = useLocation();
  const card = (
    <div className="not-found-card">
      <p className="not-found-code">404</p>
      <h1>Không tìm thấy trang</h1>
      <p>
        Đường dẫn <span className="mono">{pathname}</span> không có trong Consent Admin.
      </p>
      <Link className="btn primary" to={signedIn ? "/consents" : "/login"}>
        {signedIn ? "Về danh sách consent" : "Về trang đăng nhập"}
      </Link>
    </div>
  );

  if (signedIn) {
    return <main className="page not-found">{card}</main>;
  }

  return (
    <section className="not-found-screen">
      <Logo />
      {card}
    </section>
  );
}
