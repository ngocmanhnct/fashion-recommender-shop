import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  // Vitest đọc chung file này. Test nằm ở tests/ (cấu trúc giống src/), chạy trong jsdom.
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.test.{js,jsx}'],
    setupFiles: ['tests/setup.js'],
    restoreMocks: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      exclude: ['src/main.jsx'],
      // lcov ghi đường dẫn tính từ gốc repo (frontend/src/...) để SonarQube khớp được file.
      reporter: ['text-summary', 'html', 'json-summary', ['lcov', { projectRoot: '..' }]],
    },
  },
});
