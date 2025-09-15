import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue(), vueDevTools()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/api': {
        // 代理到后端实际服务。后端 context-path 为 /example
        target: 'http://localhost:8101',
        changeOrigin: true,
        secure: false,
        ws: true, // 启用 WebSocket 代理
        rewrite: (path) => path.replace(/^\/api/, '/example/api'),
      },
    },
  },
})

