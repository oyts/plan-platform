import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { pwaBuild } from './scripts/pwa-build.ts';
const proxyTarget = process.env.PLAN_ACCEPTANCE_API === '1' ? 'http://127.0.0.1:43131' : 'http://127.0.0.1:8080';
const proxy = { '/api': proxyTarget, '/actuator': proxyTarget };
export default defineConfig({
  plugins: [vue(), pwaBuild()],
  resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  server: { port: 43128, strictPort: true, proxy },
  preview: { proxy },
});
