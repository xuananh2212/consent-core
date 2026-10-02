import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ConfigProvider } from "antd";
import viVN from "antd/locale/vi_VN";
import App from "./App.jsx";
import "./styles/global.css";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <ConfigProvider
      locale={viVN}
      theme={{
        token: {
          colorPrimary: "#e36a12",
          borderRadius: 12,
          fontFamily: '"Be Vietnam Pro", sans-serif'
        }
      }}
    >
      <App />
    </ConfigProvider>
  </StrictMode>
);
