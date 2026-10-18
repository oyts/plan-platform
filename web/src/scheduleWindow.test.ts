import {describe,it,expect,vi} from 'vitest';
import {schedulePages} from './scheduleWindow';
describe('bounded schedule window',()=>{
  it('reads all pages rather than silently showing only the first 100',async()=>{const read=vi.fn(async(page:number)=>({content:Array.from({length:page?5:100},(_,n)=>({id:String(page*100+n)})),page,size:100,totalElements:105,totalPages:2}));expect((await schedulePages(read)).length).toBe(105);expect(read).toHaveBeenCalledTimes(2);});
  it('refuses oversized or changing windows and duplicate page rows',async()=>{await expect(schedulePages(async()=>({content:[],page:0,size:100,totalElements:501,totalPages:6}))).rejects.toThrow('500');await expect(schedulePages(async page=>({content:[{id:'same'}],page,size:1,totalElements:2,totalPages:2}))).rejects.toThrow('未完整');await expect(schedulePages(async page=>({content:[{id:String(page)}],page,size:1,totalElements:page?3:2,totalPages:2}))).rejects.toThrow('已变化');});
});
