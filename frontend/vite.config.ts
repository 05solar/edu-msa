import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 서버는 5173 포트. /api 요청은 백엔드(docker compose 기본 8088)로 프록시한다.
// 백엔드 포트가 다르면 아래 target 값을 수정한다.
const BACKEND_ORIGIN = 'http://localhost:8088'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: BACKEND_ORIGIN,
        changeOrigin: true,
      },
    },
  },
})
