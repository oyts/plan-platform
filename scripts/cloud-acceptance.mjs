// Run only against an isolated MySQL acceptance API with synthetic accounts.
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const origin = process.argv[2];
if (!/^http:\/\/127\.0\.0\.1:\d+$/.test(origin || '')) throw new Error('SSH-forwarded loopback acceptance API required.');
const password = 'CloudAcceptance123!';
async function api(path, token, data, method = data ? 'POST' : 'GET', status = 200, headers = {}) {
  const response = await fetch(origin + '/api' + path, { signal: AbortSignal.timeout(15000), method, headers: { 'Content-Type': 'application/json', ...headers, ...(token ? {Authorization:`Bearer ${token}`} : {}) }, body: data ? JSON.stringify(data) : undefined });
  assert.equal(response.status, status, `${method} ${path}`);
  return status === 204 || status >= 400 ? null : response.json();
}
async function account() {
  return api('/auth/register', null, { email:`cloud-${randomUUID()}@example.test`, password, displayName:'云端合成验收' }, 'POST', 201);
}
const config = await api('/auth/config'); assert.equal(config.serverVersion, '0.1.4'); assert.equal(config.todoEnabled, true);
const owner = await account(), other = await account();
const token = owner.accessToken, outsider = other.accessToken;
const plan = await api('/plans', token, {name:'云端验收周期',description:'合成数据',startDate:'2026-10-01',endDate:'2026-10-31',restDays:[7]}, 'POST', 201);
assert.deepEqual(plan.restDays,[7]);
await api(`/plans/${plan.id}/tasks`, outsider, undefined, 'GET', 404);
assert.equal((await api('/plans', outsider)).length,0);
const taskBody = {date:'2026-10-12',title:'云端验收任务',category:'学习',description:'',plannedMinutes:20,acceptance:[],resourceUrl:'',required:true,sortOrder:0};
const task = await api(`/plans/${plan.id}/tasks`,token,taskBody,'POST',201);
await api(`/tasks/${task.id}`,token,{...taskBody,title:'新标题',version:task.version},'PUT');
await api(`/tasks/${task.id}`,token,{...taskBody,title:'过期版本',version:task.version},'PUT',409);
const line = await api('/mainlines',token,{name:'云端验收路线',description:'合成数据',color:'#0033aa',executionMode:'FLEXIBLE'},'POST',201);
const stage = await api(`/mainlines/${line.id}/stages`,token,{name:'基础',description:'',sortOrder:0},'POST',201);
const step = await api(`/mainlines/${line.id}/items`,token,{title:'路线步骤',description:'',acceptance:[],plannedMinutes:20,sortOrder:0,routing:{stageId:stage.id,dependencyMode:'NONE',prerequisiteIds:[]}},'POST',201);
assert.equal(step.stageId,stage.id);
assert.equal((await api('/mainlines',outsider)).length,0);
await api(`/mainlines/${line.id}/stages`,outsider,{name:'不允许的阶段',sortOrder:2},'POST',404);
const key=randomUUID(),body={title:'幂等待办',note:'恢复演练保留内容',dueDate:'2026-10-12'};
const todo=await api('/todos',token,body,'POST',201,{'X-Idempotency-Key':key});
const replay=await api('/todos',token,body,'POST',201,{'X-Idempotency-Key':key});
assert.equal(todo.id,replay.id);
await api(`/todos/${todo.id}`,outsider,{...body,done:false,deleted:false,version:todo.version},'PUT',404);
const done=await api(`/todos/${todo.id}`,token,{...body,done:true,deleted:false,version:todo.version},'PUT');
assert.equal(done.done,true);
await api(`/todos/${todo.id}`,token,{...body,done:false,deleted:false,version:todo.version},'PUT',409);
const deleted=await api(`/todos/${todo.id}`,token,{...body,done:true,deleted:true,version:done.version},'PUT');
assert.equal((await api('/todos?from=2026-10-12&to=2026-10-12',token)).length,0);
await api(`/todos/${todo.id}`,token,{...body,done:true,deleted:false,version:deleted.version},'PUT');
assert.equal((await api('/todos?from=2026-10-12&to=2026-10-12',token)).length,1);
const login=await fetch(origin+'/api/auth/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({email:owner.user.email,password})});
assert.equal(login.status,200); assert.match(login.headers.get('set-cookie'),/; Secure/i); assert.match(login.headers.get('set-cookie'),/HttpOnly/i);
await api('/plans',null,undefined,'GET',401);
console.log('MySQL acceptance PASS: version/capabilities, plans/rest days/tasks, route stages, TODO CRUD/restore, ownership, 409, idempotency and Secure HttpOnly cookie. Synthetic records retained in isolated acceptance database.');
