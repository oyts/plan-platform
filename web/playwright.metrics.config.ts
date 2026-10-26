import {defineConfig} from '@playwright/test';import base from './playwright.config';
export default defineConfig({...base,testMatch:['metrics.spec.ts','product-home-refresh.spec.ts'],workers:1,globalTeardown:'./e2e/metrics-teardown.ts',webServer:[
 {command:'node ../scripts/start-metrics-fixture.mjs',url:'http://127.0.0.1:43180/actuator/health',timeout:120000,reuseExistingServer:false},
 {command:'node ../scripts/start-metrics-preview.mjs',url:'http://127.0.0.1:43130',reuseExistingServer:false},
]});
