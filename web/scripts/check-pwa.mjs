import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const required = ['dist/index.html', 'dist/manifest.webmanifest', 'dist/sw.js', 'dist/icon.svg'];
const missing = required.filter((file) => !existsSync(resolve(root, file)));
if (missing.length) throw new Error(`PWA 构建产物缺失：${missing.join(', ')}`);
const index = readFileSync(resolve(root, 'dist/index.html'), 'utf8');
const manifest = readFileSync(resolve(root, 'dist/manifest.webmanifest'), 'utf8');
const worker = readFileSync(resolve(root, 'dist/sw.js'), 'utf8');
if (!index.includes('manifest.webmanifest')) throw new Error('index.html 未引用 Web App Manifest。');
if (!manifest.includes('"display": "standalone"')) throw new Error('Manifest 未声明 standalone 安装模式。');
if (!worker.includes("url.pathname.startsWith('/api/')")) throw new Error('Service Worker 必须跳过私有 API 缓存。');
console.log('PWA asset and offline-safety checks passed.');
