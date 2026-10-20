import {defineConfig} from '@playwright/test';
import base from './playwright.config';
export default defineConfig({...base,testMatch:['personal-templates.spec.ts','personal-data.spec.ts'],webServer:process.env.PLAN_TEMPLATE_EXTERNAL_SERVERS==='1'?[]:[
 {command:'node scripts/start-acceptance-api.mjs',url:'http://127.0.0.1:43131/actuator/health',env:{PLAN_ACCEPTANCE_JAR:'backend/target-templates/plan-platform-api.jar'},timeout:120_000,reuseExistingServer:false},
 {command:'npm run preview -- --host 127.0.0.1 --port 43130 --strictPort',url:'http://127.0.0.1:43130',env:{PLAN_ACCEPTANCE_API:'1'},reuseExistingServer:false},
]});
