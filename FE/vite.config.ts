/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000
  },
  css: {
    devSourcemap: true
  },
  // Vitest dùng chung cấu hình này (alias '~'...). Test là logic thuần nên chạy trong Node, không cần trình duyệt giả.
  test: {
    include: ['src/**/*.test.ts']
  },
  resolve: {
    alias: {
      '~': path.resolve(__dirname, './src')
    }
  }
})
