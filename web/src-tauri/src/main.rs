#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    if std::env::args().any(|a|a=="--reminder-clear"){let _=plan_platform_desktop_lib::reminders::clear();return;}
    if plan_platform_desktop_lib::reminders::probe_requested(){return;}
    if plan_platform_desktop_lib::reminders::worker_requested(){
        tauri::async_runtime::block_on(plan_platform_desktop_lib::reminders::worker());return;
    }
    plan_platform_desktop_lib::run();
}
