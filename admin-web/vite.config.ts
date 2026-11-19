import { defineConfig, loadEnv } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {\n  const env = loadEnv(mode, process.cwd(), "");\n  return {
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    proxy: { "/api": { target: env.VITE_API_PROXY_TARGET || "http://localhost:8080", changeOrigin: true } },
  },
  build: { target: "es2022", chunkSizeWarningLimit: 250, sourcemap: false },
  test: { environment: "node", clearMocks: true },
});