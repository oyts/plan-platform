import {describe,it,expect} from 'vitest';
import {templatePending,templateDependencies,type Blueprint} from './personalTemplates';
describe('personal template safeguards',()=>{
 it('retains only known explicit submission paths, not automatic replay',()=>{const p={schemaVersion:1,url:'/structure-templates/capture',body:{request:{name:'合成'},confirmed:true,operationKey:'a'.repeat(36),checksum:'b'.repeat(64)}};expect(templatePending(p)).toEqual(p);expect(templatePending(null)).toBeNull();expect(()=>templatePending({...p,url:'/ai/sessions/anything/jobs'})).toThrow();expect(()=>templatePending({...p,body:{...p.body,confirmed:false}})).toThrow();});
 it('shows prerequisite titles and distinguishes inheritance',()=>{const b:Blueprint={schemaVersion:1,kind:'ROUTE',parent:{execution_mode:'SEQUENTIAL'},rows:{mainline_items:[{id:'a',title:'先修'}]}};expect(templateDependencies({dependency_mode:'EXPLICIT',prerequisite_ids_json:'["a"]'},b)).toBe('先修');expect(templateDependencies({dependency_mode:'INHERIT'},b)).toBe('依赖前序步骤');expect(templateDependencies({dependency_mode:'NONE'},b)).toBe('无前置要求');});
});
