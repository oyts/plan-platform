use cookie::Cookie;
use keyring::Entry;
use reqwest::{cookie::Jar, header, Client, Method};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{collections::BTreeMap, fs, path::PathBuf, sync::Arc, time::{Duration, SystemTime, UNIX_EPOCH}};
use url::Url;

const DEFAULT_SERVER: &str = "http://127.0.0.1:8080";

#[derive(Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DesktopConfig {
    pub server_url: String,
}

pub struct DesktopState {
    pub server: Url,
    pub client: Client,
    pub jar: Arc<Jar>,
    config_path: PathBuf,
}

#[derive(Deserialize)]
pub struct NativeRequest {
    pub method: String,
    pub path: String,
    pub query: Option<String>,
    pub headers: BTreeMap<String, String>,
    pub body: Option<String>,
}

#[derive(Serialize)]
pub struct NativeResponse {
    pub status: u16,
    pub headers: BTreeMap<String, String>,
    pub body: String,
}

#[derive(Serialize, Deserialize)]
struct SavedCredential {
    cookie: String,
    expires_at: u64,
}

pub fn validate_server(value: &str) -> Result<Url, String> {
    let mut server = Url::parse(value.trim()).map_err(|_| "请输入完整服务器地址，例如 https://plan.example.cn".to_string())?;
    let host = server.host_str().ok_or("服务器地址缺少主机名。")?;
    let loopback = matches!(host, "localhost" | "127.0.0.1" | "[::1]" | "::1");
    if server.scheme() != "https" && !(server.scheme() == "http" && loopback) {
        return Err("云服务器必须使用 HTTPS；本机调试可使用 http://127.0.0.1。".into());
    }
    if !server.username().is_empty() || server.password().is_some() || server.query().is_some() || server.fragment().is_some() || !matches!(server.path(), "" | "/") {
        return Err("仅填写服务器根地址，不包含账号、/api、查询参数或页面路径。".into());
    }
    server.set_path("/");
    Ok(server)
}

pub fn request_url(server: &Url, path: &str, query: Option<&str>) -> Result<Url, String> {
    if !path.starts_with("/api/") || path.contains("..") || path.contains('%') || path.contains('\\') || path.contains('?') || path.contains('#') {
        return Err("仅允许访问当前服务器的 /api/ 接口。".into());
    }
    let mut target = server.clone();
    target.set_path(path);
    target.set_query(query);
    Ok(target)
}

fn now() -> u64 { SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs() }

fn credential_entry(server: &Url) -> Result<Entry, String> {
    let id = format!("{:x}", Sha256::digest(server.as_str().as_bytes()));
    Entry::new("PlanCabin.RefreshSession", &id).map_err(|_| "无法打开 Windows 凭据管理器。".into())
}

impl DesktopState {
    pub fn load(config_path: PathBuf) -> Result<Self, String> {
        // A damaged configuration must not prevent opening the connection settings.
        // Preserve the original file until the user explicitly saves a replacement.
        let config = fs::read(&config_path).ok().and_then(|bytes| serde_json::from_slice::<DesktopConfig>(&bytes).ok());
        let server = config.and_then(|value| validate_server(&value.server_url).ok())
            .unwrap_or_else(|| validate_server(DEFAULT_SERVER).unwrap());
        let (client, jar) = Self::make_client(&server)?;
        Ok(Self { server, client, jar, config_path })
    }

    fn make_client(server: &Url) -> Result<(Client, Arc<Jar>), String> {
        let jar = Arc::new(Jar::default());
        let entry = credential_entry(server)?;
        match entry.get_password() {
            Ok(value) => {
                if let Ok(saved) = serde_json::from_str::<SavedCredential>(&value) {
                    if saved.expires_at > now() {
                        if let Ok(mut cookie) = Cookie::parse(saved.cookie) {
                            cookie.set_max_age(cookie::time::Duration::seconds((saved.expires_at - now()).min(i64::MAX as u64) as i64));
                            jar.add_cookie_str(&cookie.to_string(), server);
                        }
                    }
                    // Expired credentials are never restored; a locked vault must not block opening settings.
                    else { let _ = entry.delete_credential(); }
                }
            }
            Err(keyring::Error::NoEntry) => (),
            // A locked/unavailable vault means a new login, not an unopenable client.
            Err(_) => (),
        }
        let client = Client::builder().cookie_provider(jar.clone()).timeout(Duration::from_secs(20))
            .redirect(reqwest::redirect::Policy::none()).build().map_err(|_| "无法初始化桌面连接。")?;
        Ok((client, jar))
    }

    pub fn config(&self) -> DesktopConfig { DesktopConfig { server_url: self.server.as_str().trim_end_matches('/').into() } }

    pub fn clear_session(&mut self) -> Result<(), String> {
        let deletion = match credential_entry(&self.server)?.delete_credential() {
            Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
            Err(_) => Err("无法清除 Windows 登录凭据，请检查凭据管理器。".to_string()),
        };
        let jar = Arc::new(Jar::default());
        self.client = Client::builder().cookie_provider(jar.clone()).timeout(Duration::from_secs(20))
            .redirect(reqwest::redirect::Policy::none()).build().map_err(|_| "无法重置桌面会话。")?;
        self.jar = jar;
        deletion
    }

    pub fn save_server(&mut self, value: &str) -> Result<DesktopConfig, String> {
        let server = validate_server(value)?;
        let (client, jar) = Self::make_client(&server)?;
        let config = DesktopConfig { server_url: server.as_str().trim_end_matches('/').into() };
        if let Some(parent) = self.config_path.parent() { fs::create_dir_all(parent).map_err(|_| "无法创建桌面配置目录。")?; }
        let encoded = serde_json::to_vec_pretty(&config).map_err(|_| "无法生成桌面配置。")?;
        fs::write(&self.config_path, encoded).map_err(|_| "无法保存服务器地址。")?;
        self.server = server; self.client = client; self.jar = jar;
        Ok(config)
    }

    fn remember_session(&self, response: &reqwest::Response) -> Result<(), String> {
        for raw in response.headers().get_all(header::SET_COOKIE) {
            let Ok(raw) = raw.to_str() else { continue };
            let Ok(cookie) = Cookie::parse(raw.to_owned()) else { continue };
            if cookie.name() != "plan_refresh" { continue; }
            let entry = credential_entry(&self.server)?;
            let ttl = cookie.max_age().map(|value| value.whole_seconds()).unwrap_or(0);
            if cookie.value().is_empty() || ttl <= 0 {
                match entry.delete_credential() { Ok(()) | Err(keyring::Error::NoEntry) => (), Err(_) => return Err("无法清除 Windows 登录凭据。".into()) }
            } else {
                let saved = SavedCredential { cookie: raw.into(), expires_at: now().saturating_add(ttl as u64) };
                entry.set_password(&serde_json::to_string(&saved).map_err(|_| "无法编码登录凭据。")?)
                    .map_err(|_| "无法安全保存登录会话，请检查 Windows 凭据管理器。")?;
            }
        }
        Ok(())
    }

    pub async fn request(&self, request: NativeRequest) -> Result<NativeResponse, String> {
        let method = Method::from_bytes(request.method.as_bytes()).map_err(|_| "请求方法无效。")?;
        if !matches!(method, Method::GET | Method::POST | Method::PUT | Method::PATCH | Method::DELETE) { return Err("不支持该请求方法。".into()); }
        let target = request_url(&self.server, &request.path, request.query.as_deref())?;
        let mut builder = self.client.request(method, target);
        for (name, value) in &request.headers {
            if matches!(name.to_ascii_lowercase().as_str(), "authorization" | "content-type" | "accept" | "x-idempotency-key" | "if-match") {
                builder = builder.header(name, value);
            }
        }
        if let Some(body) = request.body { builder = builder.body(body); }
        let response = builder.send().await.map_err(|_| "无法连接服务器，请检查服务器地址、网络与 HTTPS 证书。")?;
        self.remember_session(&response)?;
        let status = response.status().as_u16();
        let mut headers = BTreeMap::new();
        for name in ["content-type", "content-disposition", "x-idempotent-replay", "etag"] {
            if let Some(value) = response.headers().get(name).and_then(|value| value.to_str().ok()) { headers.insert(name.into(), value.into()); }
        }
        let body = response.text().await.map_err(|_| "无法读取服务器响应。")?;
        Ok(NativeResponse { status, headers, body })
    }
}

pub async fn probe(value: &str) -> Result<String, String> {
    let server = validate_server(value)?;
    let client = Client::builder().timeout(Duration::from_secs(8)).redirect(reqwest::redirect::Policy::none()).build().map_err(|_| "无法创建测试连接。")?;
    let response = client.get(server.join("api/auth/config").map_err(|_| "服务器地址无效。")?).send().await.map_err(|_| "连接失败，请检查网络、证书和服务器地址。")?;
    if !response.status().is_success() { return Err(format!("账号接口返回 HTTP {}，该地址可能仍是旧版服务。", response.status().as_u16())); }
    let json: serde_json::Value = serde_json::from_str(&response.text().await.map_err(|_| "无法读取测试响应。")?).map_err(|_| "该地址未返回新版账号接口。")?;
    if !json.get("registrationEnabled").is_some_and(serde_json::Value::is_boolean) { return Err("该地址未返回新版账号接口。".into()); }
    Ok("连接成功，已识别 Spring Boot 多用户服务。".into())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn server_validation_requires_https_except_loopback() {
        assert!(validate_server("https://plan.example.cn/").is_ok());
        assert!(validate_server("http://127.0.0.1:8080").is_ok());
        for bad in ["http://115.29.176.141", "https://user:pass@example.cn", "https://example.cn/api", "https://example.cn?x=1", "file:///tmp/test"] { assert!(validate_server(bad).is_err(), "{bad}"); }
    }
    #[test]
    fn requests_stay_on_configured_server() {
        let server = validate_server("https://plan.example.cn").unwrap();
        assert_eq!(request_url(&server, "/api/plans", Some("page=2")).unwrap().as_str(), "https://plan.example.cn/api/plans?page=2");
        for bad in ["https://evil.cn/api/x", "//evil.cn/api/x", "/api/../admin", "/api/%2e%2e/x", "/api/x?url=evil", "/actuator/health"] { assert!(request_url(&server, bad, None).is_err(), "{bad}"); }
    }

    #[tokio::test]
    async fn local_logout_does_not_require_a_running_server() {
        use reqwest::cookie::CookieStore;
        let listener = std::net::TcpListener::bind("127.0.0.1:0").unwrap();
        let server = validate_server(&format!("http://{}", listener.local_addr().unwrap())).unwrap();
        let entry = credential_entry(&server).unwrap();
        struct Cleanup(Entry);
        impl Drop for Cleanup { fn drop(&mut self) { let _ = self.0.delete_credential(); } }
        let cleanup = Cleanup(entry);
        let saved = SavedCredential { cookie: "plan_refresh=test-only; Path=/api/auth; Max-Age=60".into(), expires_at: now() + 60 };
        cleanup.0.set_password(&serde_json::to_string(&saved).unwrap()).unwrap();
        let (client, jar) = DesktopState::make_client(&server).unwrap();
        let endpoint = server.join("api/auth/refresh").unwrap();
        assert!(jar.cookies(&endpoint).is_some());
        let mut state = DesktopState { server: server.clone(), client, jar, config_path: PathBuf::new() };
        state.clear_session().unwrap();
        assert!(state.jar.cookies(&endpoint).is_none());
        assert!(matches!(cleanup.0.get_password(), Err(keyring::Error::NoEntry)));
        let (_, restarted) = DesktopState::make_client(&server).unwrap();
        assert!(restarted.cookies(&endpoint).is_none());
    }

    #[tokio::test]
    async fn native_cookie_survives_restart_and_is_cleared_on_logout() {
        use std::{io::{Read, Write}, net::TcpListener};
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let address = listener.local_addr().unwrap();
        let server = validate_server(&format!("http://{address}")).unwrap();
        // A unique loopback port keeps this synthetic credential separate from real sessions.
        struct Cleanup(Entry);
        impl Drop for Cleanup { fn drop(&mut self) { let _ = self.0.delete_credential(); } }
        let cleanup = Cleanup(credential_entry(&server).unwrap());
        let worker = std::thread::spawn(move || {
            for index in 0..4 {
                let (mut socket, _) = listener.accept().unwrap();
                socket.set_read_timeout(Some(Duration::from_secs(10))).unwrap();
                let mut request = Vec::new();
                while !request.windows(4).any(|part| part == b"\r\n\r\n") {
                    let mut buffer = [0; 1024];
                    let size = socket.read(&mut buffer).unwrap();
                    assert!(size > 0); request.extend_from_slice(&buffer[..size]);
                }
                let request = String::from_utf8(request).unwrap().to_ascii_lowercase();
                let has_cookie = request.contains("cookie: plan_refresh=synthetic-session");
                assert_eq!(has_cookie, matches!(index, 1 | 2));
                let cookie = match index {
                    0 => "Set-Cookie: plan_refresh=synthetic-session; Path=/api/auth; HttpOnly; Max-Age=60\r\n",
                    2 => "Set-Cookie: plan_refresh=; Path=/api/auth; HttpOnly; Max-Age=0\r\n",
                    _ => "",
                };
                write!(socket, "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n{cookie}Content-Length: 2\r\nConnection: close\r\n\r\n{{}}").unwrap();
            }
        });
        let make_state = || {
            let (client, jar) = DesktopState::make_client(&server).unwrap();
            DesktopState { server: server.clone(), client, jar, config_path: PathBuf::new() }
        };
        let request = |path: &str| NativeRequest { method: "GET".into(), path: path.into(), query: None, headers: BTreeMap::new(), body: None };
        let first = make_state();
        let response = first.request(request("/api/auth/login")).await.unwrap();
        assert!(!response.headers.contains_key("set-cookie"));
        assert!(cleanup.0.get_password().is_ok());
        drop(first);
        let resumed = make_state();
        resumed.request(request("/api/auth/refresh")).await.unwrap();
        resumed.request(request("/api/auth/logout")).await.unwrap();
        assert!(matches!(cleanup.0.get_password(), Err(keyring::Error::NoEntry)));
        drop(resumed);
        make_state().request(request("/api/auth/refresh")).await.unwrap();
        worker.join().unwrap();
    }
}
