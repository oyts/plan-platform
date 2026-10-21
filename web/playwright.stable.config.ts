import {defineConfig} from '@playwright/test';
import base from './playwright.config';
export default defineConfig({...base,testMatch:['stable-trial.spec.ts','personal-templates.spec.ts','product-home-refresh.spec.ts'],webServer:process.env.PLAN_STABLE_EXTERNAL_SERVERS==='1'?[]:[
 {command:'node scripts/start-acceptance-api.mjs',url:'http://127.0.0.1:43131/actuator/health',env:{PLAN_ACCEPTANCE_JAR:'backend/target-stable/plan-platform-api.jar'},timeout:120_000,reuseExistingServer:false},
 {command:'npm run preview -- --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',env:{PLAN_ACCEPTANCE_API:'1'},reuseExistingServer:false},
]});
