# Android 客户端构建与验收

本阶段将现有 Vue 3 页面打包为独立 Android APK，使用 Java 连接同一套 Spring Boot API。安装包内含界面，不依赖远程网页加载；登录和业务操作仍需要新版后端。本次不替换阿里云上的旧版服务，也不发布到应用商店。

## 已实现的能力

- Android 7.0 及以上安装；服务器设置可在登录前进入，不绑定家庭或校园网 IP。
- Java 层固定服务器请求、HTTPS 证书校验、禁止跨站重定向；正式包只允许 HTTPS，测试包额外允许本机和 Android 模拟器宿主地址的 HTTP。
- Access Token 只在页面内存中；默认 `plan_refresh` 会话由 Android Keystore 的不可导出 AES 密钥加密，密文保存在私有存储中。会话按服务器隔离、使用绝对到期时间，退出登录删除对应会话；禁用系统备份，避免迁移密文或敏感本地缓存到其他设备。
- 后端不可达时退出也会尝试清除本机凭据并返回登录页，但这不代表服务器或其他设备的会话已被撤销；网络恢复后须重新登录并执行“退出所有设备”。本地清理失败会明确提示。
- 离线队列沿用服务端幂等键，按服务器和用户隔离。冲突留在同步中心，不直接覆盖。冷启动离线登录和全量离线数据浏览尚不支持；计时和完成操作仍需联网。
- 系统返回键导航和首页退出确认；CSV 和 Markdown 导出通过 Android 分享菜单保存或分享，无需申请存储、相机、定位权限。
- 手动覆盖升级：同一应用 ID、同一签名、递增 `versionCode`。当前没有静默安装或在线自动下载更新。

## 使用本地测试包

文件位于 `android-artifacts/PlanCabin-0.1.0-debug.apk`。它是开发测试包，不能当作面向公众的正式发布包。

1. 在电脑双击 `start-desktop-server.bat`，启动本机 43129 端口的独立 Spring Boot 测试库。它不会改动原数据库或云端注册设置。
2. 手机开启开发者选项和 USB 调试，使用数据线连接电脑，并在手机上确认调试授权。开启 USB 调试会扩大设备管理权限，仅授权自己的电脑，测试后可关闭。
3. 在项目根目录打开 PowerShell，执行以下命令。若使用 Android Studio 的 SDK，请将路径换为自己的 `platform-tools/adb.exe`。

```powershell
& .\.desktop-tools\android-sdk\platform-tools\adb.exe devices
& .\.desktop-tools\android-sdk\platform-tools\adb.exe reverse tcp:43129 tcp:43129
& .\.desktop-tools\android-sdk\platform-tools\adb.exe install -r .\android-artifacts\PlanCabin-0.1.0-debug.apk
```

4. 手机上打开“计划舱”，在登录页进入“服务器连接与客户端更新”，填写 `http://127.0.0.1:43129`，测试并保存。USB 转发使手机的这个地址指向电脑，不必开放校园网端口。拔掉数据线、重启设备后可能需要重新执行转发命令。
5. Windows EXE 也连接 `http://127.0.0.1:43129`；浏览器连接同一后端的方式见 [Windows 客户端说明](windows-desktop.md)。三端使用相同账号，才能看到相同业务数据。
6. Android 模拟器可直接使用 `http://10.0.2.2:43129`。正式部署验收完成后，才改为新版 API 的 HTTPS 根地址。

不要使用旧版同步密钥当作新版密码。当前公网域名上的旧版服务不提供新版账号接口，连接测试会拒绝它。首次 APK 安装不需要等待网络证书；公网 API 的 HTTPS 连接才需要有效服务器证书。

43129 是 API 端口，不是网页入口。手机浏览器直接打开它的根路径可能返回 HTTP 401；应打开已安装的 APK，在应用内部配置该地址。

## 构建与测试

需要 Node.js 22 或以上、JDK 21、Android SDK platform 36 和 build-tools 36.0.0。当前电脑的便携工具放在 Git 忽略目录 `.desktop-tools`，没有把 SDK、运行时、缓存或签名密钥加入仓库；其他电脑应先安装工具并设置 `JAVA_HOME`、`ANDROID_HOME`。中文 Windows 工作目录将 Gradle 缓存和生成的构建输出分别放到系统临时目录 `plan-cabin-gradle`、`plan-cabin-android-build`，避免测试启动器的路径编码问题；源码不移动。可指定自己的纯英文 `GRADLE_USER_HOME` 和 `PLAN_ANDROID_BUILD_ROOT`。最终 APK 仍复制回项目的 `android-artifacts`。

双击 `build-android.bat` 可运行前端测试、编译界面、同步资源、执行 Java 单元测试、构建并验证调试 APK。首次构建需下载 Gradle 与依赖；Gradle 使用官方精简包并校验 SHA256。如当前进程设置了 `HTTPS_PROXY`，脚本会将无用户名密码的 HTTP 代理传递给 JVM，不修改系统代理。

若 Maven Central 经代理返回 403，但直连可访问，在当前 PowerShell 临时设置 `$env:NO_PROXY="$env:NO_PROXY,repo.maven.apache.org,repo.gradle.org"` 后重试。脚本会同步这份直连例外到 JVM；关闭窗口后不会保留设置。不要因此关闭 HTTPS 校验或改用未知镜像。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action build
```

Java 单元测试覆盖 HTTPS、调试 HTTP 范围、固定 API 路径、请求头限制、导出文件名和 Cookie 有效期。前端测试覆盖平台分派、请求响应、409 冲突、账号队列隔离和中文导出。编译与单元测试不等于真机验收；Keystore、系统分享、返回键和后台恢复须按下方清单实际测试。

系统备份同时使用 `allowBackup=false` 和显式云备份、设备迁移排除规则，依据 [Android 官方备份说明](https://developer.android.com/identity/data/autobackup)。仅关闭 `allowBackup` 不能保证所有厂商设备都禁用设备间迁移；实际设备行为仍需验收。

已提供 `SessionVaultInstrumentedTest` 真机测试，使用随机测试服务器标识验证 Keystore 加密、重建读取、跨服务器隔离与删除，不访问真实账号。在项目根目录执行 `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/android-device.ps1 -Action instrument`；脚本不自动卸载主应用，测试辅助包会留在设备上。

最新验收结果：后端 13 项、前端 32 项、Windows 原生 4 项、Android Java 6 项、独立浏览器 16 项通过；Web/PWA 构建检查通过，EXE 与调试 APK 构建成功，APK 签名校验通过。2026-10-02 在 iQOO Neo10 Pro 安装 APK，并通过 1 项 Keystore 真机测试。手机手工业务、分享、后台恢复、三端原生联调和升级回归仍待完成；未执行远程 GitHub 工作流或生产部署。完整范围见 [跨客户端验收](cross-client-acceptance.md)。

## 真机与跨端验收

在一台实际 Android 手机上依次验证：

1. 安装、冷启动、登录前修改地址、断网启动设置；不应白屏。
2. 注册或登录、关闭后重开会话恢复；退出后重启不能自动恢复该账号。
3. 两个账号的数据互不可见；切换服务器后重新登录，不得重放另一个服务器的队列。
4. 手机新增计划，PC 刷新可见；PC 编辑后手机刷新可见。同一实体出现版本冲突时显示 409 并提供手动处理，不静默覆盖。
5. 任务开始、暂停、完成、休息日补做、顺序主线锁定、统计日期与分页。
6. 在任务计时期间锁屏、切换后台、重新打开，按后端记录恢复计时，不叠加一个本地计时器。
7. 已登录时断开后端，普通编辑进入队列；恢复后按序重放。计时、完成、删除不伪装成离线成功。
8. CSV 与 Markdown 分享到文件管理器或其他应用，中文不乱码。取消分享不应影响计划数据。
9. 返回键按页面层级返回，首页需要确认退出；键盘、状态栏、导航栏不遮挡输入框与保存按钮。
10. 使用同一签名且更高 `versionCode` 覆盖安装，保留配置与会话；不同签名应由 Android 拒绝覆盖。无效证书与远程 HTTP 应被拒绝。

未完成这份验收前，不替换旧版生产服务，不删除旧版数据库或备份。

## 正式签名与发布

开发测试密钥在 `.android-signing/debug.keystore`，密码为 Android 标准调试密码；**它不是正式发布身份**。正式密钥由维护者单独生成并离线备份，遗失后无法正常给已有安装用户覆盖升级。切勿提交 keystore、密码或把它们上传到公开 OSS。

维护者可交互式生成密钥，避免把密码写进命令历史：

```powershell
keytool -genkeypair -keystore .android-signing/release.jks -alias plancabin -keyalg RSA -keysize 3072 -validity 10000
```

设置当前进程环境变量 `PLAN_ANDROID_KEYSTORE`、`PLAN_ANDROID_STORE_PASSWORD`、`PLAN_ANDROID_KEY_ALIAS`、`PLAN_ANDROID_KEY_PASSWORD`，再执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/android.ps1 -Action release
```

脚本缺少签名配置会停止，不会把未签名包或调试签名包冒充正式包。发布前保持 `web/package.json` 版本与 `web/android/app/build.gradle` 的 `versionName` 一致，每次递增 `versionCode`。正式包关闭明文 HTTP；使用构建输出的 SHA256 核对文件来源。APK 签名与服务器 HTTPS 证书是两件不同的事。

新增 Android GitHub Actions 工作流只构建调试包和运行测试，不上传签名密钥、不自动发布；远程工作流仍需在推送到 GitHub 后实际验证。

## 学习与下一阶段

阅读顺序：`capacitor.config.ts` → `src/desktop.ts` → `src/api/desktopAdapter.ts` → `android/.../PlanNativePlugin.java` → `SessionVault.java` → `TransportRulesTest.java`。这一实现复用 Vue，同时保留可学习的 Java 原生请求、密钥管理与系统交互代码。

下一阶段先完成 Web、EXE、APK 的同账号回归，再准备 Spring Boot 灰度部署与旧版迁移演练。跨端验收可用 6.1 Sol + Medium；涉及部署、回滚、鉴权和迁移改动时建议 6.1 Sol + High。微信小程序在账号及部署稳定后单独开发。
