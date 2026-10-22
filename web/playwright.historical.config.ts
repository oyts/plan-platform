import {defineConfig} from '@playwright/test';
import base from './playwright.config';
// UI-only fixtures: every API call is intercepted; SMTP and migration transactions are tested separately.
export default defineConfig({...base,testMatch:['**/historical-closure.spec.ts'],webServer:[],use:{...base.use,baseURL:'http://127.0.0.1:43166'}});
