import { defineConfig, devices } from '@playwright/test'

// Chạy với cả hệ thống đã dựng sẵn bằng docker compose (FE ở :3000, BE ở :8081, dữ liệu mẫu của profile demo).
// Playwright không tự khởi động app: CI chạy `docker compose up` ở bước trước, chạy tay thì làm như vậy rồi mới chạy test.
export default defineConfig({
  testDir: './tests',
  timeout: 30_000,
  // Trên CI cho chạy lại một lần nếu hỏng, và chỉ lúc đó mới ghi trace (ảnh từng bước, network) để xem vì sao hỏng.
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure'
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }]
})
