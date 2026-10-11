import { describe, expect, it } from 'vitest';
import { serverConnectionIssue, validateServerConfig } from './serverCompatibility';
describe('server compatibility diagnostics',()=>{
  it('accepts earlier Spring Boot config without requiring new capabilities',()=>{ expect(validateServerConfig({registrationEnabled:false}).registrationEnabled).toBe(false); });
  it.each(['<html>old application</html>',{ok:true,service:'plan-console-sync'},null])('rejects non-account responses',value=>{ expect(()=>validateServerConfig(value)).toThrow('新版账号接口'); });
  it('distinguishes a reachable legacy endpoint from a network failure',()=>{ expect(serverConnectionIssue({isAxiosError:true,response:{status:404}})).toContain('HTTP 404'); expect(serverConnectionIssue({isAxiosError:true})).toContain('后端是否启动'); });
});
