import {defineConfig} from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Конфигурация Vite.
 *
 * Прокси /api -> http://localhost:8080 нужен, чтобы во время разработки
 * фронтенд (порт 5173) и API (порт 8080) были для браузера «одним источником»:
 * не нужен CORS, не мешают preflight-запросы. В production фронтенд отдаёт
 * VITE_API_URL (см. .env.example), и CORS-конфиг на бэкенде уже под это настроен.
 */
export default defineConfig({
  plugins: [react()],
  server: {
      allowedHosts: true,
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
