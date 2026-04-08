import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/user': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/voucher': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/voucher-order': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
