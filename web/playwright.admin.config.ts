import {defineConfig} from '@playwright/test';import base from './playwright.config';
export default defineConfig({...base,testMatch:['admin-center.spec.ts','account-ai-operations.spec.ts','feedback.spec.ts'],webServer:[
 {command:'node ../scripts/start-admin-center-fixture.mjs',url:'http://127.0.0.1:43180/actuator/health',timeout:120000,reuseExistingServer:process.env.PLAN_ADMIN_REUSE==='1'},
 {command:'node node_modules/vite/bin/vite.js preview --config vite.admin.config.ts --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',reuseExistingServer:false},
]});
