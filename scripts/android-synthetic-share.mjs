// Opens the real Android chooser with a credential-free synthetic backup.
// Recipient selection is a separate, observed UI step; never sends automatically to a contact.
import {execFile} from 'node:child_process';import {promisify} from 'node:util';import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {randomUUID,createHash} from 'node:crypto';import {join} from 'node:path';import {fileURLToPath} from 'node:url';
import {chromium,expect} from '../web/node_modules/@playwright/test/index.mjs';import {selectDevice,assertPortsFree} from './native-regression-support.mjs';
if(process.argv.length!==3||process.argv[2]!=='--owner-approved-qq-my-computer')throw Error('Explicit synthetic QQ My Computer approval required');
const root=fileURLToPath(new URL('../',import.meta.url)),out=join(root,'acceptance-results/historical-closure/external-share',new Date().toISOString().replace(/[:.]/g,'-'));
const pkg='cn.zijun701.plan.mobile.regression',port=43183,exec=promisify(execFile),adbPath=join(root,'.desktop-tools/android-sdk/platform-tools/adb.exe');let serial,browser,forward=false;
async function adb(...args){return(await exec(adbPath,[...(serial?['-s',serial]:[]),...args],{windowsHide:true,timeout:20000,maxBuffer:4e6})).stdout.trim();}
const report={status:'FAIL',recipient:'QQ My Computer (owner-approved; not yet selected)',formalAppTouched:false};
try{
 await mkdir(out,{recursive:true});await assertPortsFree([port]);serial=selectDevice(await adb('devices'));
 const installed=(await adb('shell','pm','path',pkg)).match(/^package:(\/data\/app\/[^\r\n]+\/base.apk)$/m)?.[1];if(!installed)throw Error('Independent APK missing');expect((await adb('shell','sha256sum',installed)).split(/\s+/)[0]).toBe('362119e8b6a73412a49f892ed7d52099f55e470a0cda7040d978b995ac1f4624');
 const focus=(await adb('shell','dumpsys','window')).split(/\r?\n/).filter(x=>x.includes('mCurrentFocus=')&&!x.includes('mCurrentFocus=null'));if(focus.length!==1||!focus[0].includes(pkg+'/'))throw Error('Independent app must be foreground');
 const pid=await adb('shell','pidof',pkg);if(!/^\d+$/.test(pid))throw Error('Unique regression process required');await adb('forward',`tcp:${port}`,`localabstract:webview_devtools_remote_${pid}`);forward=true;
 browser=await chromium.connectOverCDP(`http://127.0.0.1:${port}`,{noDefaults:true});const page=browser.contexts().flatMap(c=>c.pages()).find(p=>/^https?:\/\/localhost/.test(p.url()));if(!page)throw Error('No independent WebView');
 const records=Object.fromEntries(['plans','task_templates','plan_tags','plan_tasks','task_progress','time_entries','plan_milestones','daily_reflections','mainlines','mainline_stages','mainline_items','todos','weekly_reviews','daily_reviews','personal_templates'].map(x=>[x,[]]));
 const now=new Date().toISOString();records.todos.push({id:randomUUID(),title:'合成外部分享待办',note:'仅用于本人QQ我的电脑接收验证；无真实计划或账号资料。',due_date:'2026-10-23',done:false,deleted:false,completed_at:null,created_at:now,updated_at:now});
 const body=JSON.stringify({format:'plan-cabin-private-backup',schemaVersion:4,sourceVersion:'0.1.19',exportedAt:now,records});
 const fileName='plan-cabin-synthetic-share-'+randomUUID()+'.json';await writeFile(join(out,fileName),body,{flag:'wx'});report.fileName=fileName;report.sha256=createHash('sha256').update(body).digest('hex');report.bytes=Buffer.byteLength(body);
 await page.evaluate(async args=>window.Capacitor.Plugins.PlanNative.execute({command:'share_file',args}),{fileName,body,mimeType:'application/json'});
 const native=await adb('shell','run-as',pkg,'cat','cache/exports/'+fileName);expect(createHash('sha256').update(native).digest('hex')).toBe(report.sha256);
 report.status='WAITING_OBSERVED_RECIPIENT_SELECTION';report.nativeFileBytesVerified=true;
 console.log('Synthetic chooser opened; source/native bytes match. No recipient selected by this script.');
}catch(error){report.error=error.message;process.exitCode=1;console.error('Stopped: '+error.message);}
finally{if(browser)await browser.close().catch(()=>{});if(forward)await adb('forward','--remove',`tcp:${port}`).catch(()=>{});await mkdir(out,{recursive:true});await writeFile(join(out,'report.json'),JSON.stringify(report,null,2));console.log('Evidence: '+out);}
