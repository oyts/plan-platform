import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {bridgeManifest,bridgeConfig,bridgePaths} from './legacy-android-bridge.mjs';
import {clientStaticConfig} from './client-static-config.mjs';
const base=clientStaticConfig(readFileSync(new URL('../web/nginx.production.conf',import.meta.url),'utf8'));
const manifest={version:'0.1.7',versionCode:12,minimumSupportedVersionCode:11,size:1234,sha256:'a'.repeat(64),packageName:'cn.zijun701.plan.app',notes:'Optional legacy migration'};
test('legacy manifest is optional, canonical HTTPS, bounded and has no credentials',()=>{
  const result=bridgeManifest(manifest,'bridge-test',10000,1000);
  assert.equal(result.minimumSupportedVersionCode,1);
  assert.equal(result.url,'https://plan.zijun701.cn/client-updates/android/bridge-test/PlanCabin-0.1.7-release.apk');
  assert.equal(result.migrationExpiresAt,'1970-01-01T02:46:40.000Z');
  assert.ok(!/token|password|cookie/i.test(JSON.stringify(result)));
});
test('expired, excessive, invalid APK and traversal inputs fail closed',()=>{
  for(const expires of [999,1000,1000+72*3600+1,1.5])assert.throws(()=>bridgeManifest(manifest,'bridge-test',expires,1000));
  for(const run of ['../escape','bridge-x; command','bridge-x/other','x'])assert.throws(()=>bridgePaths(run,'0.1.7'));
  assert.throws(()=>bridgeManifest({...manifest,packageName:'other'},'bridge-test',10000,1000));
  assert.throws(()=>bridgeManifest({...manifest,versionCode:8},'bridge-test',10000,1000));
});
test('only two exact files are served; both expire, no broad APK alias or query expiration',()=>{
  const result=bridgeConfig(base,'bridge-test',manifest,10000);
  assert.equal((result.match(/secure_link "/g)||[]).length,2);
  assert.equal((result.match(/if \(\$secure_link = "0"\)/g)||[]).length,2);
  assert.equal((result.match(/^\s+alias /gm)||[]).length,2);
  assert.ok(result.includes('location ^~ /client-updates/android/ { return 404; }'));
  assert.ok(!result.includes('$arg_'));
  assert.ok(!result.includes('autoindex on'));
  assert.ok(result.includes('proxy_pass http://backend:8080/api/;'));
  assert.ok(result.includes('location = /client-updates/windows/latest.json'));
  assert.ok(result.includes('max_ranges 0;'));
});
test('unknown and already modified production configs are not overwritten',()=>{
  assert.throws(()=>bridgeConfig(base.replace('android/latest.json','other/latest.json'),'bridge-test',manifest,10000));
  const modified=bridgeConfig(base,'bridge-test',manifest,10000);
  assert.throws(()=>bridgeConfig(modified,'bridge-next',manifest,11000));
});
