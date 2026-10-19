import { defineConfig } from '@playwright/test';
import base from './playwright.config';

// Every API used by these four specs is intercepted in the spec itself.
// Start the loopback preview separately (port 43138). Windows process-tree
// teardown must not prevent the reporter from finishing after the last test.
export default defineConfig({
  ...base,
  testMatch: ['ai-workbench.spec.ts', 'ai-planning.spec.ts', 'ai-adjustment.spec.ts', 'precise-colors.spec.ts'],
  use: { ...base.use, baseURL: 'http://127.0.0.1:43138' },
  webServer: [],
});
