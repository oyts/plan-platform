import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import assert from 'node:assert/strict';
const read=path=>readFileSync(new URL(path,import.meta.url),'utf8');
test('AI mount reuse accepts only one approved read-only key and never reads contents',()=>{
  const script=read('./verify-ai-secret-mount.sh');
  assert.ok(script.includes('bind|/etc/plan-platform-ai/deepseek-api-key|/run/secrets/deepseek-api-key|false'));
  assert.ok(script.includes('100:101:600'));assert.ok(script.includes('--network none --read-only --cap-drop ALL'));
  assert.ok(!/\bcat\b/.test(script));assert.ok(!/chmod|chown/.test(script));
});
test('production opt-in is bounded and preserves private credential file',()=>{
  const config=read('./production-ai.yml');
  for(const text of ['AI_MAX_CONCURRENT: "1"','AI_QUEUE_CAPACITY: "2"','read_only: true','create_host_path: false'])assert.ok(config.includes(text));
  assert.ok(!config.includes('AI_API_KEY:'));
  assert.ok(read('./production-compose.sh').includes('$root/production-ai.yml'));
});
test('migration drill clears candidate DB environment and verifies rollback without key or published port',()=>{
  const script=read('./ai-migration-drill.sh');
  assert.ok(script.includes('unset PLAN_MYSQL_DATABASE PLAN_MYSQL_BACKUP_DIR PLAN_COMPOSE_PROJECT'));
  assert.ok(script.includes('cmp production-business-before.txt production-business-rollback.txt'));
  assert.ok(script.includes('-e AI_ENABLED=false -e AI_API_KEY_FILE='));
  assert.ok(!script.includes('--mount'));assert.ok(!script.includes('-p 127.0.0.1:'));
});
