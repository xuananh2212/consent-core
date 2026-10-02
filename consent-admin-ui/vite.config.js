import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "");
  const apiProxy = {
    "/api": {
      target: env.API_PROXY_TARGET || "http://127.0.0.1:8081",
      changeOrigin: true
    }
  };

  return {
    plugins: [react()],
    server: {
      host: "127.0.0.1",
      port: 4180,
      strictPort: true,
      proxy: apiProxy,
      watch: {
        usePolling: true,
        interval: 300
      }
    },
    preview: {
      host: "127.0.0.1",
      port: 4180,
      proxy: apiProxy
    }
  };
});
