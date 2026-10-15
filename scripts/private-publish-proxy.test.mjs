import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
test('private Android activation reloads the unchanged web proxy after backend-only recreation',()=>{
  const script=readFileSync(new URL('./publish-invited-android.sh',import.meta.url),'utf8');
  assert.match(script,/reload_web_proxy\(\)[\s\S]*nginx -t && docker exec plan-platform-web-1 nginx -s reload/);
  assert.match(script,/compose up -d --no-deps backend \|\| ! reload_web_proxy; then rollback/);
});
test('both immediate and retained rollback resolve the restored backend address',()=>{
  const script=readFileSync(new URL('./publish-invited-android.sh',import.meta.url),'utf8');
  const rollback=script.slice(script.indexOf('rollback()'),script.indexOf('if [ -f "$managed/current.json" ]; then cp'));
  assert.match(rollback,/compose up -d --no-deps backend[\s\S]*reload_web_proxy/);
  assert.match(script,/printf 'docker exec plan-platform-web-1 nginx -t\\ndocker exec plan-platform-web-1 nginx -s reload\\n' >> "\$stage\/rollback.sh"/);
});
