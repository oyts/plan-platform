import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {clientStaticConfig} from './client-static-config.mjs';
const base=readFileSync(new URL('../web/nginx.production.conf',import.meta.url),'utf8');
test('static overlay retains HTTPS backend proxy and explicitly holds Android',()=>{
  const rendered=clientStaticConfig(base);
  assert.ok(rendered.includes('proxy_pass http://backend:8080/api/;'));
  assert.ok(rendered.includes('proxy_set_header X-Forwarded-Proto https;'));
  assert.ok(rendered.includes('location = /client-updates/android/latest.json { return 404; }'));
  assert.equal((rendered.match(/no-store/g)||[]).length,2);
  assert.equal((rendered.match(/immutable/g)||[]).length,2);
  assert.ok(!rendered.includes('autoindex on'));
});
test('unexpected config and double overlay are rejected',()=>{
  assert.throws(()=>clientStaticConfig(base.replace('location /downloads/','location /other/')));
  assert.throws(()=>clientStaticConfig(clientStaticConfig(base)));
});
test('production runtime and deployed override agree on Shanghai without changing memory or registration',()=>{
  for(const path of ['../docker-compose.production.yml','./production-timezone.yml']) {
    const config=readFileSync(new URL(path,import.meta.url),'utf8');
    assert.ok(config.includes('TZ: Asia/Shanghai'));
    assert.ok(config.includes('-Duser.timezone=Asia/Shanghai'));
    assert.ok(config.includes('-Xmx320m'));
  }
  const production=readFileSync(new URL('../docker-compose.production.yml',import.meta.url),'utf8');
  assert.ok(production.includes('REGISTRATION_ENABLED: "false"'));
  assert.ok(production.includes('REFRESH_COOKIE_SECURE: "true"'));
});
