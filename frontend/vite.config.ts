import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig(({ command, mode }) => {
  const environment = loadEnv(mode, ".", "VITE_USE_MOCKS");
  if ((command === "build" || mode === "production") && environment.VITE_USE_MOCKS === "true") {
    throw new Error("VITE_USE_MOCKS must be false for frontend builds and production mode. Use npm run dev for visual mock development.");
  }

  return {
    plugins: [react(), tailwindcss()],
    server: {
      port: 5173,
      strictPort: true,
    },
  };
});
