import {defineConfig} from '@playwright/test';
import shared from './playwright.config';
export default defineConfig({...shared,testMatch:'ai-long-planning.spec.ts',webServer:[
  {command:'node ../scripts/start-ai-long-fixture.mjs',url:'http://127.0.0.1:43174/actuator/health',timeout:120000,reuseExistingServer:process.env.PLAN_AI_LONG_REUSE==='1'},
  {command:'node node_modules/vite/bin/vite.js preview --config vite.ai-long.config.ts --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',reuseExistingServer:process.env.PLAN_AI_LONG_PREVIEW_REUSE==='1'},
]});
