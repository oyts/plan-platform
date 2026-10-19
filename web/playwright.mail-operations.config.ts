import {defineConfig} from '@playwright/test';
import base from './playwright.config';
// These tests explicitly intercept all API calls. Real API/MySQL checks are separate.
export default defineConfig({...base,testMatch:['**/email-verification.spec.ts','**/operations.spec.ts'],webServer:[],use:{...base.use,baseURL:'http://127.0.0.1:43150'}});
