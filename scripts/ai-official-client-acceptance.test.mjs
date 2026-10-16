import test from 'node:test';
import assert from 'node:assert/strict';
import {validatePaidBudget} from './ai-official-client-acceptance.mjs';
const fresh=()=>({maximum:5,submitted:0,requests:[],platforms:{}});
test('fresh campaign permits only known platforms',()=>{
  for(const kind of ['web','windows','android'])assert.doesNotThrow(()=>validatePaidBudget(fresh(),kind));
  assert.throws(()=>validatePaidBudget(fresh(),'unknown'));
});
test('started, failed or completed platforms never receive an automatic retry',()=>{
  for(const state of ['STARTED','FAIL','PASS']){const budget=fresh();budget.platforms.web=state;assert.throws(()=>validatePaidBudget(budget,'web'));}
});
test('malformed or inconsistent paid counts fail closed',()=>{
  for(const count of [-1,0.5,6,NaN,'0']){const budget=fresh();budget.submitted=count;assert.throws(()=>validatePaidBudget(budget,'web'));}
  const budget=fresh();budget.submitted=1;assert.throws(()=>validatePaidBudget(budget,'web'));
  assert.throws(()=>validatePaidBudget({...fresh(),maximum:50},'web'));
});
