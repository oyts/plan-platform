import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './e2e', fullyParallel: false, workers: 1, retries: 0,
  timeout: 45_000, expect: { timeout: 10_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:43130', timezoneId: 'Asia/Shanghai', locale: 'zh-CN',
    serviceWorkers: 'block', screenshot: 'only-on-failure', trace: 'retain-on-failure',
  },
  projects: [
    { name: 'desktop-web', use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } } },
    { name: 'mobile-web', use: { browserName: 'chromium', viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true } },
  ],
  webServer: [
    { command: 'node scripts/start-acceptance-api.mjs', url: 'http://127.0.0.1:43131/actuator/health', timeout: 120_000, reuseExistingServer: false },
    { command: 'npm run preview -- --host 127.0.0.1 --port 43130 --strictPort', url: 'http://127.0.0.1:43130', env: { PLAN_ACCEPTANCE_API: '1' }, reuseExistingServer: false },
  ],
});
