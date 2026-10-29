import { defineConfig, devices } from '@playwright/test';

// Test E2E (Playwright) chạy trên cả hệ thống thật: frontend, backend, MySQL đang chạy sẵn
// (docker compose up -d), không giả lập API như Vitest. Xem docs/coding-convention.md Mục 2.6.
const isCi = Boolean(process.env.CI);

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: true,
  forbidOnly: isCi,
  // CI chạy lại 1 lần để phân biệt lỗi thật với lỗi chập chờn (báo "flaky" trong kết quả).
  retries: isCi ? 1 : 0,
  workers: isCi ? 2 : undefined,
  reporter: isCi ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:3000',
    locale: 'vi-VN',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
