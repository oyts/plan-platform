import {describe,expect,it} from 'vitest';
import {PASSWORD_MISMATCH,registrationPasswordError} from './registration';
describe('registration password confirmation',()=>{
  it('accepts two matching valid passwords',()=>expect(registrationPasswordError('SyntheticPassword123!','SyntheticPassword123!')).toBe(''));
  it('rejects mismatch and confirmation left empty',()=>{expect(registrationPasswordError('SyntheticPassword123!','SyntheticPassword124!')).toBe(PASSWORD_MISMATCH);expect(registrationPasswordError('SyntheticPassword123!','')).toContain('再次');});
  it('checks rules even for programmatic submissions',()=>{for(const value of ['short1','lettersOnly','12345678901','a1'+'x'.repeat(71)])expect(registrationPasswordError(value,value)).not.toBe('');});
  it('does not trim or silently normalize passwords',()=>{expect(registrationPasswordError(' SyntheticPassword123! ','SyntheticPassword123!')).toBe(PASSWORD_MISMATCH);expect(registrationPasswordError(' SyntheticPassword123! ',' SyntheticPassword123! ')).toBe('');});
});
