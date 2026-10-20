//! One bounded, OS-launched request per wake. No refresh cookie, password or paid API.
use serde::{Deserialize, Serialize};
use std::{time::{SystemTime, UNIX_EPOCH, Duration}, process::Command};
use crate::transport::validate_server;

#[derive(Serialize,Deserialize)]
#[serde(rename_all="camelCase")]
pub struct Registration { pub id:String, pub token:String, pub expires_at:String }
#[derive(Serialize,Deserialize)]
#[serde(rename_all="camelCase")]
struct Saved { server:String, owner:String, registration:Registration }
#[derive(Serialize)]
#[serde(rename_all="camelCase")]
pub struct Status { enabled:bool, permission:Option<bool>, permission_detail:String, device_id:Option<String>, owner:Option<String>, platform:&'static str }
#[derive(Deserialize)]
#[serde(rename_all="camelCase")]
struct Poll { server_time:i64, next_wake_at:i64, notices:Vec<Notice>,active_keys:Vec<String> }
#[derive(Deserialize)]
#[serde(rename_all="camelCase")]
struct Notice { key:String,title:String,body:String,path:String,expires_at:i64 }
fn identity()-> &'static str { if cfg!(feature="native-regression") {"cn.zijun701.plan.desktop.regression"}else{"cn.zijun701.plan.desktop"} }
fn task_name()-> &'static str { if cfg!(feature="native-regression") {"PlanCabin-Regression-Reminders"}else{"PlanCabin-Reminders"} }
fn entry()->Result<keyring::Entry,String>{keyring::Entry::new("PlanCabin.ReminderDevice.v1",identity()).map_err(|_|"无法访问提醒授权存储。".into())}
fn now()->i64{SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_millis().min(i64::MAX as u128) as i64}
fn saved()->Option<Saved>{entry().ok()?.get_password().ok().and_then(|s|serde_json::from_str(&s).ok())}
pub fn xml(value:&str)->String{value.replace('&',"&amp;").replace('<',"&lt;").replace('>',"&gt;").replace('"',"&quot;").replace('\'',"&apos;")}
#[cfg(windows)]
fn notifier()->windows::core::Result<windows::UI::Notifications::ToastNotifier>{
    // The headless wake does not run Tauri's GUI COM initialization.
    thread_local!{static READY:std::cell::Cell<bool>=const{std::cell::Cell::new(false)};}
    READY.with(|ready|->windows::core::Result<()>{
        if !ready.get(){let initialized=unsafe{windows::Win32::System::WinRT::RoInitialize(windows::Win32::System::WinRT::RO_INIT_MULTITHREADED)};if let Err(e)=initialized{if e.code().0 as u32!=0x80010106{return Err(e);}}let id=windows::core::HSTRING::from(identity());unsafe{windows::Win32::UI::Shell::SetCurrentProcessExplicitAppUserModelID(windows::core::PCWSTR(id.as_ptr()))}?;ready.set(true);}Ok(())
    })?;
    windows::UI::Notifications::ToastNotificationManager::CreateToastNotifierWithId(&identity().into())
}
#[cfg(windows)] fn permission()->Option<bool>{notifier().and_then(|n|n.Setting()).ok().map(|s|s==windows::UI::Notifications::NotificationSetting::Enabled)}
#[cfg(not(windows))] fn permission()->Option<bool>{Some(false)}
#[cfg(windows)] fn permission_detail()->String{match notifier().and_then(|n|n.Setting()){Ok(v)=>format!("SYSTEM_SETTING_{}",v.0),Err(e)=>format!("API_ERROR_{:08X}",e.code().0 as u32)}}
#[cfg(not(windows))] fn permission_detail()->String{"UNSUPPORTED".into()}
pub fn status()->Status{let s=saved();Status{enabled:s.is_some(),permission:permission(),permission_detail:permission_detail(),device_id:s.as_ref().map(|s|s.registration.id.clone()),owner:s.map(|s|s.owner),platform:"WINDOWS"}}
pub fn install(server:&str,owner:String,registration:Registration)->Result<Status,String>{
    let server=validate_server(server)?.to_string();
    if permission()==Some(false){return Err("系统通知被禁用，请在 Windows 通知设置中检查。".into());}
    if !uuid(&owner)||!uuid(&registration.id)||registration.token.len()!=101||!registration.token.starts_with(&(registration.id.clone()+"."))||!hex(&registration.token[37..],64){return Err("提醒设备授权格式无效。".into());}
    clear()?;
    let s=Saved{server,owner,registration};
    entry()?.set_password(&serde_json::to_string(&s).map_err(|_|"无法保存提醒授权。")?).map_err(|_|"无法安全保存提醒授权。")?;
    if let Err(error)=schedule(now()+15000){let _=clear();return Err(error);}
    Ok(status())
}
pub fn clear()->Result<(),String>{
    match entry()?.delete_credential(){Ok(())|Err(keyring::Error::NoEntry)=>(),Err(_)=>return Err("提醒授权清理失败，请重试。".into())}
    #[cfg(windows)] {
        use std::os::windows::process::CommandExt;
        // Only this app's fixed task and toast group. Never wildcard user tasks.
        let _=Command::new("schtasks.exe").args(["/Delete","/TN",task_name(),"/F"]).creation_flags(0x08000000).output();
        if let Ok(h)=windows::UI::Notifications::ToastNotificationManager::History(){let _=h.ClearWithId(&identity().into());}
    }
    Ok(())
}
pub fn task_xml(executable:&str,at:i64)->Result<String,String>{
    let date=chrono::DateTime::from_timestamp_millis(at).ok_or("提醒时间无效。")?.format("%Y-%m-%dT%H:%M:%SZ").to_string();
    Ok(format!(r#"<?xml version="1.0" encoding="UTF-8"?><Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task"><Triggers><TimeTrigger><StartBoundary>{date}</StartBoundary><Enabled>true</Enabled></TimeTrigger></Triggers><Principals><Principal id="Author"><LogonType>InteractiveToken</LogonType><RunLevel>LeastPrivilege</RunLevel></Principal></Principals><Settings><MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy><DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries><StopIfGoingOnBatteries>false</StopIfGoingOnBatteries><StartWhenAvailable>false</StartWhenAvailable><Enabled>true</Enabled><Hidden>true</Hidden><WakeToRun>false</WakeToRun><ExecutionTimeLimit>PT30S</ExecutionTimeLimit></Settings><Actions Context="Author"><Exec><Command>{}</Command><Arguments>--reminder-worker</Arguments></Exec></Actions></Task>"#,xml(executable)))
}
fn schedule(at:i64)->Result<(),String>{
    schedule_with_argument(at,"--reminder-worker")
}
fn hex(s:&str,n:usize)->bool{s.len()==n&&s.bytes().all(|b|b.is_ascii_digit()||(b'a'..=b'f').contains(&b))}
fn uuid(s:&str)->bool{s.len()==36&&s.bytes().enumerate().all(|(i,b)|if [8,13,18,23].contains(&i){b==b'-'}else{b.is_ascii_digit()||(b'a'..=b'f').contains(&b)})}
fn destination(path:&str)->bool{if path=="/reminders"{return true;}let split=if path.starts_with("/reflection?kind="){path.split_once("&date=")}else{path.split_once("?date=")};let Some((base,date))=split else{return false;};(base=="/todos"||base.strip_prefix("/plans/").is_some_and(uuid)||matches!(base,"/reflection?kind=DAY"|"/reflection?kind=WEEK"))&&chrono::NaiveDate::parse_from_str(date,"%Y-%m-%d").is_ok()&&date.len()==10}
pub fn refresh()->Result<(),String>{prune(&[]);if saved().is_some(){schedule(now()+15000)?;}Ok(())}
fn prune(active:&[String]){
    #[cfg(windows)] {if let Ok(history)=windows::UI::Notifications::ToastNotificationManager::History(){if let Ok(items)=history.GetHistoryWithId(&identity().into()){for item in items{if let (Ok(tag),Ok(group))=(item.Tag(),item.Group()){if group=="plan-reminders"&&!active.iter().any(|key|hex(key,64)&&key.starts_with(&tag.to_string())){let _=history.RemoveGroupedTagWithId(&tag,&group,&identity().into());}}}}}}
    #[cfg(not(windows))] let _=active;
}
fn schedule_with_argument(at:i64,argument:&str)->Result<(),String>{
    #[cfg(windows)] {
        use std::os::windows::process::CommandExt;
        let exe=std::env::current_exe().map_err(|_|"无法定位已安装客户端。")?;
        let file=std::env::temp_dir().join(format!("{}-{}.xml",task_name(),std::process::id()));
        if !matches!(argument,"--reminder-worker"|"--reminder-probe-deliver"){return Err("提醒启动参数无效。".into());}
        // Task Scheduler's file importer expects a Unicode XML document (UTF-16LE + BOM).
        let document=task_xml(&exe.to_string_lossy(),at)?.replace("encoding=\"UTF-8\"","encoding=\"UTF-16\"").replace("<Arguments>--reminder-worker</Arguments>",&format!("<Arguments>{argument}</Arguments>"));
        let mut encoded=vec![0xff,0xfe];for unit in document.encode_utf16(){encoded.extend_from_slice(&unit.to_le_bytes());}
        std::fs::write(&file,encoded).map_err(|_|"无法准备系统提醒。")?;
        let output=Command::new("schtasks.exe").args(["/Create","/TN",task_name(),"/XML"]).arg(&file).arg("/F").creation_flags(0x08000000).output();
        let _=std::fs::remove_file(file);
        match output {Ok(o) if o.status.success()=>(),Ok(o)=>return Err(format!("Windows 计划任务创建失败（{}）：{}",o.status.code().unwrap_or(-1),String::from_utf8_lossy(&o.stderr).chars().take(400).collect::<String>())),Err(_)=>return Err("Windows 计划任务创建失败，提醒尚未启用。".into())}
        Ok(())
    }
    #[cfg(not(windows))] {let _=at;Err("当前平台尚不支持系统提醒。".into())}
}
pub async fn worker(){
    let Some(s)=saved() else{return};
    if permission()==Some(false){return;}
    let Ok(server)=validate_server(&s.server) else{let _=clear();return};
    let Ok(client)=reqwest::Client::builder().timeout(Duration::from_secs(8)).redirect(reqwest::redirect::Policy::none()).build()else{return};
    let response=client.post(server.join("api/reminders/poll").unwrap()).header("X-Reminder-Key",&s.registration.token).send().await;
    let mut accepted=0usize;let mut delivered=0usize;
    let next=match response {
        Ok(response) if response.status()==401=>{let _=clear();return},
        Ok(mut response) if response.status().is_success()=>{
            let mut bytes=Vec::new();
            let mut complete=false;
            loop {match response.chunk().await {Ok(Some(chunk)) if bytes.len()+chunk.len()<=65536=>bytes.extend_from_slice(&chunk),Ok(None)=>{complete=true;break},_=>break}}
            if !complete {if saved().is_some_and(|current|current.registration.token==s.registration.token){let _=schedule(now()+900000);}return;}
            if let Ok(poll)=serde_json::from_slice::<Poll>(&bytes){
                delivered=poll.notices.len();
                prune(&poll.active_keys);
                for notice in poll.notices.iter().take(20){
                    if saved().is_none_or(|current|current.registration.token!=s.registration.token){return;}
                    if notice.expires_at<=poll.server_time||!hex(&notice.key,64)||notice.title.len()>512||notice.body.len()>2048{continue;}
                    if show(&s.server,notice).is_ok(){accepted+=1;}
                }
                now()+(poll.next_wake_at-poll.server_time).clamp(15000,900000)
            }else{now()+900000}
        },
        _=>now()+900000,
    };
    live_trace(accepted,delivered);
    if saved().is_some_and(|current|current.registration.token==s.registration.token){let _=schedule(next);}
}
#[cfg(all(windows,debug_assertions,feature="native-regression"))]
fn history_count()->Option<u32>{
    windows::UI::Notifications::ToastNotificationManager::History().and_then(|h|h.GetHistoryWithId(&identity().into())).and_then(|items|items.Size()).ok()
}
#[cfg(all(not(windows),debug_assertions,feature="native-regression"))]
fn history_count()->Option<u32>{None}
#[cfg(all(debug_assertions,feature="native-regression"))]
fn live_trace(accepted:usize,delivered:usize){
    let directory=std::env::temp_dir().join("PlanCabin-Reminder-Probe");let _=std::fs::create_dir_all(&directory);
    let data=serde_json::json!({"at":now(),"identity":identity(),"showAccepted":accepted,"serverNotices":delivered,"historyCount":history_count(),"permissionDetail":permission_detail()});
    let _=std::fs::write(directory.join("live-worker.json"),data.to_string());
}
#[cfg(not(all(debug_assertions,feature="native-regression")))]
fn live_trace(_accepted:usize,_delivered:usize){}
fn show(server:&str,n:&Notice)->Result<(),String>{
    if !destination(&n.path){return Err("提醒跳转无效。".into());}
    #[cfg(windows)] {
        use windows::{Data::Xml::Dom::XmlDocument,UI::Notifications::ToastNotification};
        // Web fallback keeps navigation scoped to the same server; no arbitrary URL from native input.
        let destination=format!("{}{}",server.trim_end_matches('/'),n.path);
        let document=XmlDocument::new().map_err(|_|"通知初始化失败。")?;
        document.LoadXml(&format!(r#"<toast activationType="protocol" launch="{}"><visual><binding template="ToastGeneric"><text>计划舱</text><text>{}</text><text>{}</text></binding></visual></toast>"#,xml(&destination),xml(&n.title),xml(&n.body)).into()).map_err(|_|"通知内容无效。")?;
        let toast=ToastNotification::CreateToastNotification(&document).map_err(|_|"通知初始化失败。")?;
        toast.SetTag(&n.key[..16].into()).map_err(|_|"通知标识无效。")?;
        toast.SetGroup(&"plan-reminders".into()).map_err(|_|"通知分组无效。")?;
        notifier().and_then(|v|v.Show(&toast)).map_err(|_|"系统未接受通知。".into())
    }
    #[cfg(not(windows))] {let _=server;Err("不支持当前平台。".into())}
}
pub fn worker_requested()->bool {std::env::args().any(|a|a=="--reminder-worker")}

/// Capability probe exists only in the isolated debug identity. It has no login or server request.
#[cfg(all(debug_assertions,feature="native-regression"))]
pub fn probe_requested()->bool{
    if std::env::args().any(|a|a=="--reminder-live-status"){
        let directory=std::env::temp_dir().join("PlanCabin-Reminder-Probe");let _=std::fs::create_dir_all(&directory);
        let data=serde_json::json!({"at":now(),"identity":identity(),"historyCount":history_count(),"enabled":saved().is_some(),"permissionDetail":permission_detail()});
        let _=std::fs::write(directory.join("live-status.json"),data.to_string());return true;
    }
    let deliver=std::env::args().any(|a|a=="--reminder-probe-deliver");
    let prepare=std::env::args().any(|a|a=="--reminder-probe");
    if !deliver&&!prepare{return false;}
    let directory=std::env::temp_dir().join("PlanCabin-Reminder-Probe");let _=std::fs::create_dir_all(&directory);
    let result=if deliver{
        let notice=Notice{key:"a".repeat(64),title:"合成提醒测试".into(),body:"这是独立计划舱测试包的系统唤醒通知，不涉及真实任务。".into(),path:"/reminders".into(),expires_at:now()+60000};
        show("https://plan.zijun701.cn",&notice).map(|_|"SYSTEM_WAKE_AND_SHOW_ACCEPTED")
    }else if permission()==Some(false){Err("SYSTEM_PERMISSION_DENIED".into())}
    else{schedule_with_argument(now()+30000,"--reminder-probe-deliver").map(|_|"OS_TASK_REGISTERED")};
    let body=serde_json::json!({"stage":if deliver{"delivery"}else{"registration"},"result":result,"permissionDetail":permission_detail(),"at":now(),"identity":identity(),"realAccountUsed":false});
    let _=std::fs::write(directory.join(if deliver{"delivery.json"}else{"registration.json"}),body.to_string());true
}
#[cfg(not(all(debug_assertions,feature="native-regression")))]
pub fn probe_requested()->bool{false}

#[cfg(test)] mod tests{
    use super::*;
    #[test] fn schedule_contains_no_credential_or_elevated_principal(){let s=task_xml("E:\\合成 & test\\app.exe",1792450000000).unwrap();assert!(s.contains("合成 &amp; test"));assert!(s.contains("LeastPrivilege"));assert!(s.contains("InteractiveToken"));assert!(!s.contains("Password"));assert!(!s.contains("Repeat"));assert!(s.contains("--reminder-worker"));}
    #[test] fn notification_markup_is_escaped(){assert_eq!(xml("<&\"'>"),"&lt;&amp;&quot;&apos;&gt;");}
    #[test] fn invalid_calendar_time_is_rejected(){assert!(task_xml("app.exe",i64::MAX).is_err());}
    #[test] fn review_destinations_are_fixed_and_calendar_valid(){assert!(destination("/reflection?kind=DAY&date=2026-10-20"));assert!(destination("/reflection?kind=WEEK&date=2026-10-19"));assert!(!destination("/reflection?kind=OTHER&date=2026-10-20"));assert!(!destination("/reflection?kind=DAY&date=2026-02-30"));assert!(!destination("/reflection?kind=DAY&date=2026-10-20&token=x"));}
    #[test] fn strict_destinations_reject_extra_fields_and_invalid_dates(){assert!(destination("/todos?date=2026-10-20"));assert!(!destination("/todos?date=2026-02-30"));assert!(!destination("/todos?date=2026-10-20&token=x"));assert!(!destination("/plans/not-a-uuid?date=2026-10-20"));assert!(!uuid("------------------------------------"));}
}
