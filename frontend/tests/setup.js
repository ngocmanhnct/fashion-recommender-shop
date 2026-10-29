import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

// Không bật globals của Vitest nên Testing Library không tự dọn: dọn DOM và localStorage sau mỗi
// test để các test không ảnh hưởng nhau.
afterEach(() => {
  cleanup();
  localStorage.clear();
});
