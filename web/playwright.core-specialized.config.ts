import{defineConfig}from'@playwright/test';import base from'./playwright.config';
const kind=process.env.PLAN_CORE_BROWSER_KIND;
const matches:Record<string,string[]>={reliability:['ai-reliability.spec.ts'],long:['ai-long-planning.spec.ts'],admin:['admin-center.spec.ts','account-ai-operations.spec.ts','feedback.spec.ts'],metrics:['metrics.spec.ts'],ui:['ai-planning.spec.ts','ai-adjustment.spec.ts','historical-closure.spec.ts','client-updates.spec.ts','update-experience.spec.ts']};
if(!kind||!matches[kind])throw Error('Explicit specialized core suite required');
const port=({reliability:43171,long:43174,admin:43180,metrics:43180})[kind];
export default defineConfig({...base,testMatch:process.env.PLAN_CORE_FEEDBACK_FOLLOWUP==='1'?['feedback.spec.ts']:matches[kind],...(process.env.PLAN_CORE_LONG_FOLLOWUP==='1'?{grep:/one pane scroll surface/}:{}),globalTeardown:'./e2e/core-freeze-teardown.ts',outputDir:process.env.PLAN_CORE_BROWSER_OUTPUT,
webServer:[...(port?[{command:'node ../scripts/start-core-freeze-server.mjs '+kind,url:`http://127.0.0.1:${port}/actuator/health`,timeout:120000,reuseExistingServer:false}]:[]),{command:'node ../scripts/start-core-freeze-server.mjs preview',url:'http://127.0.0.1:43130',reuseExistingServer:false}]});
