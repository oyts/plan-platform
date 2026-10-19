import {defineConfig} from '@playwright/test';
import base from './playwright.config';
export default defineConfig({...base,testMatch:['reminders.spec.ts'],use:{...base.use,baseURL:'http://127.0.0.1:43138'},webServer:[]});
