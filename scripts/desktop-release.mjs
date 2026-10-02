import { readFileSync, writeFileSync } from 'node:fs';
import { resolve, basename } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const [installerArg, downloadUrl, ...notes] = process.argv.slice(2);
if (!installerArg || !downloadUrl) throw new Error('Usage: node scripts/desktop-release.mjs <installer.exe> <https-download-url> [release notes]');
const url = new URL(downloadUrl);
if (url.protocol !== 'https:' || url.username || url.password) throw new Error('Installer URL must be HTTPS without credentials.');
const installer = resolve(installerArg);
if (!installer.endsWith('-setup.exe')) throw new Error('Expected a Windows NSIS -setup.exe installer.');
const signature = readFileSync(installer + '.sig', 'utf8').trim();
if (!signature || !/^[A-Za-z0-9+/=]+$/.test(signature)) throw new Error('Missing or invalid updater signature. Build with the release signing key first.');
const config = JSON.parse(readFileSync(resolve(root, 'web/src-tauri/tauri.conf.json'), 'utf8'));
if (!basename(installer).includes(`_${config.version}_`)) throw new Error('Installer version differs from tauri.conf.json.');
const manifest = { version: config.version, notes: notes.join(' ') || '计划舱桌面客户端更新', pub_date: new Date().toISOString(), platforms: { 'windows-x86_64': { signature, url: url.href } } };
const destination = resolve(root, 'desktop-artifacts/latest.json');
writeFileSync(destination, JSON.stringify(manifest, null, 2) + '\n');
console.log(`Update manifest ready: ${destination}`);
