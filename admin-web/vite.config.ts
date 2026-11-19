import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: "0.0.0.0",
    proxy: { "/api": { target: process.env.VITE_API_PROXY_TARGET ?? "http://localhost:8080", changeOrigin: true } },
  },
  build: { target: "es2022", chunkSizeWarningLimit: 250, sourcemap: false },
  test: { environment: "node", clearMocks: true },
});