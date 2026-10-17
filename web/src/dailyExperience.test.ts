import { describe, it, expect } from 'vitest';
import { mondayOf, reminderCount, reminderDefaults, reminderDismissKey, dailyItemDestination, recordedTime } from './dailyExperience';
describe('daily experience rules',()=>{
  it('starts weeks on Monday across year boundaries and rejects invalid dates',()=>{
    expect(mondayOf('2026-10-18')).toBe('2026-10-12');expect(mondayOf('2026-10-19')).toBe('2026-10-19');expect(mondayOf('2027-01-01')).toBe('2026-12-28');expect(()=>mondayOf('2026-02-30')).toThrow();
  });
  it('reminders default off and only count explicitly selected modes/categories',()=>{
    const s={date:'2026-10-18',through:'2026-10-21',plan:{today:1,overdue:2,upcoming:3},todo:{today:4,overdue:5,upcoming:6},activeRoutes:99};
    const p=reminderDefaults();expect(reminderCount(s,p)).toBe(0);p.enabled=true;expect(reminderCount(s,p)).toBe(21);p.showOverdue=false;expect(reminderCount(s,p)).toBe(14);p.includeTodos=false;expect(reminderCount(s,p)).toBe(4);
  });
  it('dismissal keys isolate account and server and destinations preserve item dates',()=>{
    expect(reminderDismissKey('a','b')).not.toBe(reminderDismissKey('b','a'));
    const item={kind:'TODO' as const,id:'t',parentId:'p',title:'test',parentName:'',dueDate:'2026-10-17',status:'PENDING'};
    expect(dailyItemDestination(item)).toEqual({path:'/todos',query:{date:'2026-10-17',focus:'t'}});
    expect(dailyItemDestination({...item,kind:'PLAN'})).toEqual({path:'/schedule',query:{plan:'p',date:'2026-10-17',task:'t'}});
    expect(recordedTime(3660)).toBe('1 小时 1 分钟');expect(recordedTime(-120)).toBe('-0 小时 2 分钟');
  });
});
