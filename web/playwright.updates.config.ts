import {defineConfig} from '@playwright/test';
export default defineConfig({
 testDir:'./e2e',testMatch:['client-updates.spec.ts','update-experience.spec.ts'],outputDir:process.env.PLAN_UPDATE_UI_OUTPUT??'../acceptance-results/update-experience/browser',workers:1,retries:0,timeout:30000,reporter:'list',
 webServer:{command:'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 43146 --strictPort',url:'http://127.0.0.1:43146',reuseExistingServer:false},
 use:{baseURL:'http://127.0.0.1:43146',locale:'zh-CN',timezoneId:'Asia/Shanghai',serviceWorkers:'block',trace:'retain-on-failure',screenshot:'only-on-failure'},
 projects:[{name:'desktop-web',use:{browserName:'chromium',viewport:{width:1280,height:900}}},{name:'mobile-web',use:{browserName:'chromium',viewport:{width:390,height:844},isMobile:true,hasTouch:true}}],
});
