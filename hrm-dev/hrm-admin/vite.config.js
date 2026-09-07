import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发环境通过 Vite proxy 将 /api 代理到后端，与生产 Nginx 反代保持同源策略一致（决策 D9，免 CORS）
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    open: false,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 2048
  }
})
