import {describe,it,expect,vi} from 'vitest';
vi.mock('./api/client',()=>({api:{}}));
import {notificationDefaults,validReminderPath} from './reminders';
describe('native reminder safety',()=>{
  it('does not opt existing users into new reminders or reveal details',()=>{const p=notificationDefaults();expect([p.advanceEnabled,p.startEnabled,p.todoEnabled]).toEqual([false,false,false]);expect(p.hideDetails).toBe(true);expect(p.advanceMinutes).toBe(5);expect(p.todoTime).toBe('22:30');});
  it('allows only dated owned-object destinations without arbitrary URL schemes',()=>{expect(validReminderPath('/plans/01234567-1234-1234-1234-012345678901?date=2026-10-20')).toBe(true);expect(validReminderPath('/todos?date=2026-10-20')).toBe(true);for(const path of ['https://evil.test','/settings','/todos?date=2026-10-20&token=secret','//evil.test','/plans/../members',null])expect(validReminderPath(path)).toBe(false);});
});
