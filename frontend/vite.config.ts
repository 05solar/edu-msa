import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 서버는 5173 포트.
// /api/auth  → 인증 마이크로서비스 auth-service (docker compose 기본 8089)
// /api       → 플랫폼 backend (docker compose 기본 8088)
// 프록시는 위에 선언된 항목이 먼저 매칭되므로 더 구체적인 /api/auth 를 앞에 둔다.
const AUTH_ORIGIN = 'http://localhost:8089'
const BACKEND_ORIGIN = 'http://localhost:8088'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api/auth': {
        target: AUTH_ORIGIN,
        changeOrigin: true,
      },
      '/api': {
        target: BACKEND_ORIGIN,
        changeOrigin: true,
      },
    },
  },
})
