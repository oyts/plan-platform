import {defineConfig} from '@playwright/test';
import base from './playwright.config';
import {readFileSync} from 'node:fs';
const scope=JSON.parse(readFileSync(new URL('../scripts/core-freeze-scope.json',import.meta.url),'utf8'));
export default defineConfig({...base,testMatch:process.env.PLAN_CORE_FOLLOWUP==='1'?['offline-experience.spec.ts','workspaces.spec.ts']:scope.browserCore,globalTeardown:'./e2e/core-freeze-teardown.ts',
 outputDir:process.env.PLAN_CORE_BROWSER_OUTPUT??'../acceptance-results/core-freeze/browser-artifacts',
 webServer:[
  {command:'node ../scripts/start-core-freeze-server.mjs api',url:'http://127.0.0.1:43131/actuator/health',timeout:120000,reuseExistingServer:false},
  {command:'node ../scripts/start-core-freeze-server.mjs preview',url:'http://127.0.0.1:43130',reuseExistingServer:false},
 ]});
