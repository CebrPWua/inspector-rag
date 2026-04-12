import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 8000,
    proxy: {
      '/api-proxy/files': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api-proxy/files', '/api/files'),
      },
      '/api-proxy/qa': {
        target: 'http://localhost:8084',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api-proxy/qa', '/api/qa'),
      },
      '/api-proxy/records': {
        target: 'http://localhost:8085',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api-proxy/records', '/api/records'),
      },
      '/api-proxy/tasks': {
        target: 'http://localhost:8086',
        changeOrigin: true,
        rewrite: (path) => path.replace('/api-proxy/tasks', '/api/tasks'),
      },
    },
  },
})
