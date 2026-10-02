import { useState } from "react";
import { Alert, Button, Form, Input } from "antd";
import Logo from "../components/Logo.jsx";

export default function LoginScreen({ onLogin }) {
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(values) {
    setError("");
    setBusy(true);
    try {
      await onLogin(values.username.trim(), values.password);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }
  console.log("1");

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
        <div className="card">
          <h2>Đăng nhập</h2>
          <p className="sub">
            Dùng tài khoản vận hành để vào danh sách consent.
          </p>
          <Form
            layout="vertical"
            requiredMark={false}
            onFinish={submit}
            disabled={busy}
          >
            <Form.Item
              label="Tên đăng nhập"
              name="username"
              rules={[
                {
                  required: true,
                  whitespace: true,
                  message: "Nhập tên đăng nhập.",
                },
              ]}
            >
              <Input autoComplete="username" />
            </Form.Item>
            <Form.Item
              label="Mật khẩu"
              name="password"
              rules={[{ required: true, message: "Nhập mật khẩu." }]}
            >
              <Input.Password autoComplete="current-password" />
            </Form.Item>
            {error ? (
              <Alert
                type="error"
                showIcon
                message={error}
                style={{ marginBottom: 16 }}
              />
            ) : null}
            <Button type="primary" htmlType="submit" block loading={busy}>
              Đăng nhập
            </Button>
          </Form>
          <div className="hint">
            Tài khoản demo: <b>admin</b> / <b>admin123</b>
          </div>
        </div>
      </div>
    </section>
  );
}
