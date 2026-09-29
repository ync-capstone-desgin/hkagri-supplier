import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * Vite 개발 서버 설정.
 * /api 요청은 백엔드(8080)로 넘겨 같은 출처처럼 동작하게 한다 (세션 쿠키 유지).
 */
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
