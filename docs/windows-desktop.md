# Windows EXE 客户端

本阶段将 Vue 界面作为本地资源打包为 Tauri 2 Windows x64 客户端，而不是打开线上网页。服务端仍然是同一套 Spring Boot API。窗口中文名为“计划舱”，安装包名称为 `Plan Cabin`。

## 能力与边界

- NSIS EXE 安装、开始菜单入口、卸载、覆盖升级；安装到当前用户，不需要把软件安装到系统目录。
- Hash 路由与本地应用壳，服务器离线时仍能打开登录和连接设置；无固定局域网 IP。
- 登录前和登录后都能进入“桌面客户端”设置，测试并保存服务器根地址。只允许 HTTPS；本机 `localhost` / `127.0.0.1` / `::1` 调试可用 HTTP。
- Rust 发送 API 请求并验证服务器证书，不放宽 CORS、不忽略 TLS 错误、不允许请求跳转至其他服务器。
- Access Token 仅在 JS 内存中；Refresh Cookie 不返回给页面，原生 Cookie Jar 使用它，Windows 凭据管理器保存其有效期内的持久会话。退出登录清除对应凭据。服务端使用默认 `REFRESH_COOKIE_NAME=plan_refresh`。
- 离线写入队列按“服务器根地址 + 用户 ID”隔离，换服务器不会将旧服务器的操作发送到新服务器。尚不支持冷启动离线登录和完整离线业务数据浏览；计时操作仍须联网。
- CSV、Markdown 等 UTF-8 导出走原生传输；任意二进制附件下载尚不在本阶段范围。
- 外部备案/帮助链接在系统浏览器打开；主窗口拒绝导航到远程网页。
- 已实现签名更新检查、下载与校验安装；**本次本地测试包未绑定公开更新源，使用新安装包手动升级**。

Android APK 已在后续阶段增加，见 [Android 客户端说明](android-client.md)。macOS/Linux 安装包和微信小程序仍未交付。公网现有 `plan.zijun701.cn` 在新版 Spring Boot 部署验收前仍是旧版服务，不可直接用于新版账号登录。

## 立即测试

1. 双击项目根目录 `start-desktop-server.bat`，保留该窗口，启动本地 Spring Boot（仅监听本机 43129，避免占用其他工程的 8080）。使用独立的 `backend/data/desktop-local` H2 数据库，开启本地注册，不改动原开发数据库或云端注册开关。
2. 运行 `desktop-artifacts/Plan Cabin_0.1.0_x64-setup.exe` 安装客户端，打开“计划舱”。
3. 登录页点击“服务器连接与客户端更新”，填写 `http://127.0.0.1:43129`，测试连接后保存。初始默认是 8080，请按实际启动方式修改。
4. 使用新版账号登录。若没有账号，使用本地允许注册的环境创建；不要把旧版同步密钥当作新版密码。
5. 同一账号在浏览器和 EXE 中测试新增计划、主线锁定、任务计时、导出和冲突中心。不同账号不得看到对方计划。若要浏览器连接同一个本地测试库，在 `web` 终端设置 `$env:VITE_API_BASE='http://127.0.0.1:43129/api'` 后运行 `npm run dev`，用 `http://localhost:43128` 打开页面（默认开发 CORS 允许此地址）。关闭终端即可清除这个临时设置。
6. 关闭再打开 EXE，验证会话恢复；退出登录再重启，不应恢复旧会话。
7. 停止后端，EXE 仍能打开设置。已有登录状态下的普通编辑可以加入离线队列，计时失败应明确提示。恢复后在同步中心重放。
8. 填写旧版地址、远程 HTTP 地址、含 `/api` 的地址或无效证书地址，应拒绝保存，不应白屏。

项目自动测试不能代替真实安装、登录、卸载及升级验收，上述项目需人工走完。

## 本地构建

需要 Node.js 22+、Rust 1.99+ MSVC、Visual Studio C++ 构建工具、Windows SDK、WebView2。开发电脑上的便携 Rust 位于忽略目录 `.desktop-tools`，不随 Git 上传，也不影响系统 PATH。Windows SDK x64 构建组件安装在同一目录，通过官方安装器注册；不会随项目代码上传。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action check
powershell -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action test
```

双击 `build-desktop.bat` 生成安装包到 `desktop-artifacts`；双击 `start-desktop.bat` 启动原生开发模式（它会启动前端 Vite，请勿同时占用 43128 端口）。`PLAN_DESKTOP_VCVARS` 可指定自定义 `vcvars64.bat`。

安装机不需要 Java、Node 或 Rust；只需要服务端可访问，以及 Windows WebView2。安装器在缺少 WebView2 时下载官方运行时，因此首次安装可能需要网络。HTTPS 证书属于服务器连接安全，与 EXE 能否在本地安装无关。

## 签名发布与后续自动更新

首次由维护者生成更新密钥，存入被 Git 忽略的 `.desktop-signing`，妥善离线备份。**切勿提交私钥、把私钥上传到公开 OSS 或写入客户端。**

```powershell
cd web
npx tauri signer generate -w ../.desktop-signing/desktop.key
cd ..
$env:TAURI_SIGNING_PRIVATE_KEY = (Resolve-Path .desktop-signing/desktop.key).Path
# 如生成时设置了密码，另用进程环境变量 TAURI_SIGNING_PRIVATE_KEY_PASSWORD 传入。
powershell -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action build `
  -UpdateUrl https://plan.example.cn/downloads/windows/latest.json `
  -PublicKeyPath .desktop-signing/desktop.key.pub
```

发布前同时提升 `web/package.json`、其锁文件、`web/src-tauri/tauri.conf.json` 和 `web/src-tauri/Cargo.toml` 的版本，更新 Cargo.lock。使用同一私钥生成 EXE 和 `.sig`，再生成清单：

```powershell
node scripts/desktop-release.mjs "desktop-artifacts/Plan Cabin_0.1.0_x64-setup.exe" `
  "https://plan.example.cn/downloads/windows/Plan%20Cabin_0.1.0_x64-setup.exe" "版本说明"
```

先上传 EXE 与 `.sig`，最后更新 HTTPS `latest.json`；安装包不可改名或更改内容后继续使用旧签名。公钥内嵌客户端，更新地址内嵌构建配置；用户修改业务服务器不会修改可信更新源。没有签名或签名错误的更新应拒绝安装。

更新签名不同于 Windows Authenticode 代码签名。本阶段没有购买 Windows 代码签名证书，系统可能显示“未知发布者”；只运行自己构建或核对来源的安装包。

## 工程与安全学习点

阅读顺序：`src/desktop.ts` → `src/api/desktopAdapter.ts` → `src-tauri/src/lib.rs` → `src-tauri/src/transport.rs`。理解浏览器 Cookie 和原生 Cookie Jar 的差异、服务端幂等协议复用、凭据管理器与普通配置文件的边界、签名更新与 HTTPS 的不同作用。

后续推荐 Web、EXE、APK 跨端验收，再进行 Spring Boot 公网上线及旧版迁移；不要在客户端尚未验收时替换旧版生产服务。
