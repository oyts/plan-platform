// Pure fixture clock: no device, credentials or product data.
export function reminderWindow(nowMs, {reboot = false} = {}) {
  if (!Number.isFinite(nowMs)) throw new TypeError('Finite fixture timestamp required');
  const startAt = Math.floor((nowMs + (reboot ? 10 * 60000 : 125000)) / 60000) * 60000;
  const formatter = new Intl.DateTimeFormat('sv-SE', {timeZone:'Asia/Shanghai', year:'numeric', month:'2-digit', day:'2-digit', hour:'2-digit', minute:'2-digit', hourCycle:'h23'});
  const parts = Object.fromEntries(formatter.formatToParts(startAt).map(p => [p.type,p.value]));
  return {date:`${parts.year}-${parts.month}-${parts.day}`, hm:`${parts.hour}:${parts.minute}`, startAt, alertAt:startAt-60000};
}
export function requireFutureWindow(schedule, nowMs) {
  // Late owner handoff is an inconclusive fixture, not proof of a delivery regression.
  if (nowMs >= schedule.alertAt - 15000) throw new Error('INCONCLUSIVE_LATE_OWNER_RETURN: future reminder window exhausted; no delivery verdict');
}
export function dueWaitBudget(schedule, nowMs) {
  return Math.max(1000, schedule.alertAt + 45000 - nowMs);
}
