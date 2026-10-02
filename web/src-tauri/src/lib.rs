mod transport;

use serde::Serialize;
use tauri::{Manager, State};
use tauri_plugin_opener::OpenerExt;
use tauri_plugin_updater::{Update, UpdaterExt};
use tokio::sync::Mutex;
use transport::{DesktopConfig, DesktopState, NativeRequest, NativeResponse};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct DesktopInfo {
    version: String,
    server_url: String,
    updates_enabled: bool,
}

struct PendingUpdate(Mutex<Option<Update>>);

#[derive(Serialize)]
struct UpdateInfo { version: String, notes: Option<String> }

#[tauri::command]
async fn desktop_check_update(app: tauri::AppHandle, pending: State<'_, PendingUpdate>) -> Result<Option<UpdateInfo>, String> {
    if !updates_enabled() { return Err("当前安装包尚未配置签名更新源。".into()); }
    let endpoint = url::Url::parse(option_env!("PLAN_DESKTOP_UPDATE_URL").unwrap()).map_err(|_| "更新源地址无效。")?;
    if endpoint.scheme() != "https" { return Err("更新源必须使用 HTTPS。".into()); }
    let update = app.updater_builder().endpoints(vec![endpoint]).map_err(|error| error.to_string())?
        .timeout(std::time::Duration::from_secs(20)).build().map_err(|error| error.to_string())?
        .check().await.map_err(|error| format!("更新检查失败：{error}"))?;
    let info = update.as_ref().map(|value| UpdateInfo { version: value.version.clone(), notes: value.body.clone() });
    *pending.0.lock().await = update;
    Ok(info)
}

#[tauri::command]
async fn desktop_install_update(pending: State<'_, PendingUpdate>) -> Result<(), String> {
    let update = pending.0.lock().await.take().ok_or("请先检查并选择新版本。")?;
    update.download_and_install(|_, _| {}, || {}).await.map_err(|error| format!("更新失败，当前版本仍可使用：{error}"))
}

#[tauri::command]
async fn desktop_info(app: tauri::AppHandle, state: State<'_, Mutex<DesktopState>>) -> Result<DesktopInfo, String> {
    Ok(DesktopInfo { version: app.package_info().version.to_string(), server_url: state.lock().await.config().server_url, updates_enabled: updates_enabled() })
}

#[tauri::command]
async fn desktop_save_server(server_url: String, state: State<'_, Mutex<DesktopState>>) -> Result<DesktopConfig, String> {
    state.lock().await.save_server(&server_url)
}

#[tauri::command]
async fn desktop_probe(server_url: String) -> Result<String, String> { transport::probe(&server_url).await }

#[tauri::command]
async fn clear_native_session(state: State<'_, Mutex<DesktopState>>) -> Result<(), String> { state.lock().await.clear_session() }

#[tauri::command]
async fn desktop_request(request: NativeRequest, state: State<'_, Mutex<DesktopState>>) -> Result<NativeResponse, String> {
    state.lock().await.request(request).await
}

#[tauri::command]
fn desktop_open_url(app: tauri::AppHandle, url: String) -> Result<(), String> {
    let target = url::Url::parse(&url).map_err(|_| "链接格式无效。")?;
    if !matches!(target.scheme(), "http" | "https") || !target.username().is_empty() || target.password().is_some() { return Err("仅支持打开网页链接。".into()); }
    app.opener().open_url(target.as_str(), None::<&str>).map_err(|_| "无法打开系统浏览器。".into())
}

fn updates_enabled() -> bool {
    option_env!("PLAN_DESKTOP_UPDATE_URL").is_some_and(|value| !value.trim().is_empty()) && option_env!("PLAN_DESKTOP_UPDATE_PUBKEY").is_some_and(|value| !value.trim().is_empty())
}

pub fn run() {
    let mut builder = tauri::Builder::default().plugin(tauri_plugin_opener::init());
    if updates_enabled() {
        builder = builder.plugin(tauri_plugin_updater::Builder::new().pubkey(option_env!("PLAN_DESKTOP_UPDATE_PUBKEY").unwrap()).build());
    }
    builder.setup(|app| {
        let path = app.path().app_config_dir()?.join("desktop.json");
        app.manage(Mutex::new(DesktopState::load(path).map_err(std::io::Error::other)?));
        app.manage(PendingUpdate(Mutex::new(None)));
        tauri::WebviewWindowBuilder::from_config(app, &app.config().app.windows[0])?
            .on_navigation(|url| {
                (url.scheme() == "tauri" && url.host_str() == Some("localhost"))
                    || (matches!(url.scheme(), "http" | "https") && url.host_str() == Some("tauri.localhost"))
                    || (cfg!(debug_assertions) && url.host_str() == Some("localhost") && url.port() == Some(43128))
            }).build()?;
        Ok(())
    }).invoke_handler(tauri::generate_handler![desktop_info, desktop_save_server, desktop_probe, desktop_request, desktop_open_url, desktop_check_update, desktop_install_update, clear_native_session])
      .run(tauri::generate_context!())
      .expect("无法启动计划舱桌面客户端");
}
