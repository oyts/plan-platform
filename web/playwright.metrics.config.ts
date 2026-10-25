import {defineConfig} from '@playwright/test';import base from './playwright.config';
export default defineConfig({...base,testMatch:['metrics.spec.ts'],workers:1,webServer:[
 {command:'node ../scripts/start-metrics-fixture.mjs',url:'http://127.0.0.1:43180/actuator/health',timeout:120000,reuseExistingServer:false},
 {command:'node node_modules/vite/bin/vite.js preview --config vite.admin.config.ts --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',reuseExistingServer:false},
]});
