import { api } from './api/client';
import { isAndroid, isDesktop, isNativeClient, nativeInvoke } from './desktop';
export interface ReminderPreferences {
  advanceEnabled:boolean;advanceMinutes:number;startEnabled:boolean;todoEnabled:boolean;todoTime:string;zone:string;
  quietEnabled:boolean;quietFrom:string;quietUntil:string;hideDetails:boolean;primaryDeviceId:string|null;version:number|null;
}
export interface ReminderDevice {id:string;platform:'WINDOWS'|'ANDROID';label:string;enabled:boolean;expiresAt:string;lastSeenAt:string|null}
export interface ReminderNativeStatus {enabled:boolean;permission:boolean|null;permissionDetail?:string;exact?:boolean;deviceId?:string|null;owner?:string|null;platform:string}
export const notificationDefaults=():ReminderPreferences=>({advanceEnabled:false,advanceMinutes:5,startEnabled:false,todoEnabled:false,todoTime:'22:30',zone:'Asia/Shanghai',quietEnabled:false,quietFrom:'23:00',quietUntil:'07:00',hideDetails:true,primaryDeviceId:null,version:null});
export function validReminderPath(path:unknown):path is string {return typeof path==='string'&&(/^\/plans\/[a-f0-9-]{36}\?date=\d{4}-\d{2}-\d{2}$/.test(path)||/^\/todos\?date=\d{4}-\d{2}-\d{2}$/.test(path)||/^\/reflection\?kind=(DAY|WEEK)&date=\d{4}-\d{2}-\d{2}$/.test(path));}
export async function nativeReminderStatus():Promise<ReminderNativeStatus>{return isNativeClient()?nativeInvoke('reminder_status'):{enabled:false,permission:false,platform:'WEB'};}
export async function enableDeviceReminders(owner:string):Promise<ReminderNativeStatus>{
  if(!isNativeClient())throw new Error('网页版保留站内提醒；系统通知需要已安装的客户端。');
  const {queueCount}=await import('./offlineQueue');const {currentQueueOwner}=await import('./api/client');
  const queueOwner=currentQueueOwner();if(!queueOwner||await queueCount(queueOwner)>0)throw new Error('请先同步本地操作并处理冲突，再开启本设备提醒，避免按旧状态通知。');
  let status=await nativeReminderStatus();
  if(isAndroid()&&!status.permission)status=await nativeInvoke('reminder_permission');
  if(status.permission===false)throw new Error('系统通知未授权。没有启用本设备提醒，请在系统中检查通知权限。');
  if(status.enabled)throw new Error('本设备已开启提醒，请先关闭后再重新授权。');
  const {data}=await api.post('/reminders/devices',{platform:isDesktop()?'WINDOWS':'ANDROID',label:isDesktop()?'我的 Windows 设备':'我的 Android 设备'});
  try{return await nativeInvoke('reminder_enable',{owner,registration:data});}
  catch(error){await api.delete(`/reminders/devices/${data.id}`).catch(()=>undefined);throw error;}
}
export async function disableDeviceReminders():Promise<void>{
  const status=await nativeReminderStatus();
  // Local cancellation must succeed even offline. A stale server row cannot deliver without its device key.
  if(isNativeClient())await nativeInvoke('reminder_clear');
  if(status.deviceId)await api.delete(`/reminders/devices/${status.deviceId}`);
}
