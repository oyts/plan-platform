import {defineConfig} from '@playwright/test';
import shared from './playwright.config';
export default defineConfig({...shared,testMatch:'ai-reliability.spec.ts',webServer:[
  {command:'node ../scripts/start-ai-reliability-fixture.mjs',url:'http://127.0.0.1:43171/actuator/health',timeout:120000,reuseExistingServer:process.env.PLAN_RELIABILITY_REUSE==='1'},
  {command:'node node_modules/vite/bin/vite.js preview --config vite.reliability.config.ts --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',reuseExistingServer:false},
]});
