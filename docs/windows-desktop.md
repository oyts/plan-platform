# Windows EXE 客户端

本阶段将 Vue 界面作为本地资源打包为 Tauri 2 Windows x64 客户端，而不是打开线上网页。服务端仍然是同一套 Spring Boot API。窗口中文名为“计划舱”，安装包名称为 `Plan Cabin`。

## 能力与边界

- NSIS EXE 安装、开始菜单入口、卸载、覆盖升级；安装到当前用户，不需要把软件安装到系统目录。
- Hash 路由与本地应用壳，服务器离线时仍能打开登录和连接设置；无固定局域网 IP。
- 登录前和登录后都能进入“桌面客户端”设置，测试并保存服务器根地址。只允许 HTTPS；本机 `localhost` / `127.0.0.1` / `::1` 调试可用 HTTP。
- Rust 发送 API 请求并验证服务器证书，不放宽 CORS、不忽略 TLS 错误、不允许请求跳转至其他服务器。
- Access Token 仅在 JS 内存中；Refresh Cookie 不返回给页面，原生 Cookie Jar 使用它，Windows 凭据管理器保存其有效期内的持久会话。退出登录清除对应凭据。服务端使用默认 `REFRESH_COOKIE_NAME=plan_refresh`。
- 离线写入队列按“服务器根地址 + 用户 ID”隔离，换服务器不会将旧服务器的操作发送到新服务器。最近联网验证后的 24 小时内支持离线重开和已缓存页面浏览；普通编辑进入队列，计时和完成操作仍须联网。
- CSV、Markdown 等 UTF-8 导出走原生传输；任意二进制附件下载尚不在本阶段范围。
- 外部备案/帮助链接在系统浏览器打开；主窗口拒绝导航到远程网页。
- 0.1.1 已加入启动检查、普通更新延后、最低支持版本限制和默认关闭的自动下载。安装始终需要确认。本次签名测试包绑定现有域名的更新源，但清单尚未上线，暂不能从公网下载。见 [客户端更新与本地验收](client-updates.md)。

Android APK 已在后续阶段增加，见 [Android 客户端说明](android-client.md)。macOS/Linux 安装包和微信小程序仍未交付。正式 HTTPS `https://plan.zijun701.cn` 已启用 Spring Boot 0.1.4，可用于新版云端账号登录；公开注册仍关闭，普通成员通过邀请注册。

## 正式云端连接

已安装的 0.1.4 客户端在“服务器连接与客户端更新”中填写 `https://plan.zijun701.cn`，测试连接后保存，再登录云端账号。不要附加 `/api` 或登录路径。旧的 `http://127.0.0.1:43132` 是本机测试环境，停止测试服务器后不能连接；保存旧地址不代表云端故障。

换服务器会重新登录，本机测试计划和离线队列按原服务器保留，不会自动合并或发送到云端。正式候选包已在原目录以 `/UPDATE` 覆盖安装，首次启动前 350 个原数据文件哈希未改变；用户自行登录后，实际正式应用三模式加载、原生连接检测、正常关闭重开及会话保持已验收。详情、独立签名备份副本及公开发布条件见 [正式客户端发布](formal-client-release.md)。

## 本机开发测试

以下是独立本机环境，不是正式云端安装步骤；0.1.1 为历史测试包。不要为运行本机测试而将正式账号的服务器地址改回本机，优先使用 [独立原生回归包](native-regression.md)。

1. 双击项目根目录 `start-desktop-server.bat`，保留该窗口，启动本地 Spring Boot（仅监听本机 43129，避免占用其他工程的 8080）。使用独立的 `backend/data/desktop-local` H2 数据库，开启本地注册，不改动原开发数据库或云端注册开关。
2. 本机手工测试仅使用自己单独准备的测试客户端，不覆盖已安装的正式 0.1.4，也不要安装旧 0.1.1 包降级。若要自动回归，按 [独立原生回归包](native-regression.md) 运行 `prepare-native-regression.bat` 和 `verify-native-clients.bat`；它们使用独立测试服务，不需要上述 43129 服务。
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

## 正式签名与后续自动更新

正式长期身份已保存在 Git 忽略的 `.release-signing`，密码按当前 Windows 用户 DPAPI 保护。旧 `.desktop-signing` 是测试身份，不能用于正式发布，也不要重新生成密钥来绕过身份不一致。**切勿提交私钥、把私钥上传到公开 OSS 或写入客户端。**

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 -Action check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-formal-clients.ps1
```

构建脚本以原长期身份生成双端候选包并验证，不自动安装、上传或开放下载。发布前同时提升后端、Web、Tauri 和 Android 版本，更新对应锁文件并递增 Android 构建号；已公开的版本不得原地替换文件。正式清单生成及验收条件以 [正式客户端发布](formal-client-release.md) 与 [客户端更新](client-updates.md) 为准。

先上传 EXE 与 `.sig`，最后更新 HTTPS `latest.json`；安装包不可改名或更改内容后继续使用旧签名。公钥内嵌客户端，更新地址内嵌构建配置；用户修改业务服务器不会修改可信更新源。没有签名或签名错误的更新应拒绝安装。

更新签名不同于 Windows Authenticode 代码签名。本阶段没有购买 Windows 代码签名证书，系统可能显示“未知发布者”；只运行自己构建或核对来源的安装包。

## 工程与安全学习点

阅读顺序：`src/desktop.ts` → `src/api/desktopAdapter.ts` → `src-tauri/src/lib.rs` → `src-tauri/src/transport.rs`。理解浏览器 Cookie 和原生 Cookie Jar 的差异、服务端幂等协议复用、凭据管理器与普通配置文件的边界、签名更新与 HTTPS 的不同作用。

正式 HTTPS 三端联测已通过，详见 [云端账号原生联测](cloud-native-acceptance.md)。正式 Windows 界面与重开会话另行完成只读验收；旧服务、原应用和备份仍保留，公众下载尚未开放。正式更新清单当前返回 HTTP 404，客户端更新栏的清单获取失败不等于业务 API 故障，也不证明正式身份的跨版本升级已经通过。
