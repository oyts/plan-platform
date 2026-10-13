import { readFile } from 'node:fs/promises';
const root = new URL('../', import.meta.url);
const read = path => readFile(new URL(path, root), 'utf8');
const pkg = JSON.parse(await read('web/package.json'));
const lock = JSON.parse(await read('web/package-lock.json'));
const tauri = JSON.parse(await read('web/src-tauri/tauri.conf.json'));
const pom = await read('backend/pom.xml');
const gradle = await read('web/android/app/build.gradle');
const cargo = await read('web/src-tauri/Cargo.toml');
const cargoLock = await read('web/src-tauri/Cargo.lock');
const application = await read('backend/src/main/resources/application.yml');
const versions = [lock.version, lock.packages[''].version, tauri.version,
  pom.match(/<artifactId>plan-platform-api<\/artifactId>\s*<version>([^<]+)</)?.[1],
  gradle.match(/^\s*versionName[^\r\n]*?"([^"\r\n]+)"/m)?.[1], cargo.match(/^version = "([^"]+)"/m)?.[1],
  cargoLock.match(/name = "plan-platform-desktop"\s*version = "([^"]+)"/)?.[1],
  application.match(/^  version: (\S+)/m)?.[1]];
if (!/^\d+\.\d+\.\d+$/.test(pkg.version) || versions.some(v => v !== pkg.version)) throw new Error(`Web / API / native release versions differ: ${JSON.stringify(versions)}`);
const code = Number(gradle.match(/versionCode[^\n]*'([0-9]+)'/)?.[1]);
if (!Number.isInteger(code) || code < 9) throw new Error('Android release code must exceed the published/tested 0.1.4 code 8.');
console.log(`Release ${pkg.version}: Web, API, Windows and Android metadata agree; Android versionCode ${code}.`);
