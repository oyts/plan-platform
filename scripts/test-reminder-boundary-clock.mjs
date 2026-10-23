import {test} from 'node:test';
import assert from 'node:assert/strict';
import {reminderWindow,requireFutureWindow,dueWaitBudget} from './reminder-boundary-clock.mjs';
test('reboot fixture leaves at least eight minutes before notification including minute rounding',()=>{
  for(const second of [0,1,30,59]){
    const now=Date.parse(`2026-10-23T04:22:${String(second).padStart(2,'0')}Z`),s=reminderWindow(now,{reboot:true});
    assert.ok(s.alertAt-now>=8*60000);assert.ok(s.alertAt-now<=9*60000);
    assert.equal(Date.parse(`${s.date}T${s.hm}:00+08:00`),s.startAt);
    assert.ok(dueWaitBudget(s,now)>120000);
  }
});
test('late return is explicitly inconclusive and does not produce a delivery assertion',()=>{
  const s=reminderWindow(Date.parse('2026-10-23T04:22:21Z'),{reboot:true});
  assert.doesNotThrow(()=>requireFutureWindow(s,s.alertAt-16000));
  for(const now of [s.alertAt-15000,s.alertAt,s.startAt+60000])assert.throws(()=>requireFutureWindow(s,now),/INCONCLUSIVE_LATE_OWNER_RETURN/);
});
test('Shanghai midnight and ordinary short fixture remain deterministic',()=>{
  const now=Date.parse('2026-10-23T15:55:59Z'),s=reminderWindow(now,{reboot:true});
  assert.equal(s.date,'2026-10-24');assert.equal(s.hm,'00:05');
  assert.ok(reminderWindow(now).startAt-now>=65000);
  assert.throws(()=>reminderWindow(NaN),/Finite/);
});
