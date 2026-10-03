# 客户端更新与本地验收

0.1.1 为 Windows EXE 与 Android APK 加入更新提示、下载和安装入口。已完成本地实现、两端手动覆盖安装及更新偏好保存验收；两端已通过应用内下载、校验并更新至本地验收版 0.1.2，用户确认重开后的账号、计划和休息日配置保留，再次检查无重复更新提示。强制升级和异常下载的实机验收尚未完成。阿里云线上服务未改动。界面沿用现有白蓝配色，在服务器设置中展示安装版本、发布说明和明确的操作状态。

## 用户如何更新

启动、网络恢复或每 15 分钟检查一次；设置里也可手动检查。普通更新可以“稍后处理”。默认不勾选“自动下载更新（安装前仍需确认）”，用户主动勾选后才保存偏好，偏好与更新源绑定。安装前必须确认并保存正在编辑的内容；不会自动关闭应用或静默安装。Android 还需要允许本应用安装更新，并在系统安装界面确认。

必要升级以最低支持版本为准，不根据版本号是否“大版本”自行推断。低于最低支持版本的客户端暂停业务编辑与提交，包括队列重放；同步中心、设置、导出和登录入口保留，不清空本地队列。未保存的当前业务页面被暂停交互而非立即卸载。之前已获取的必要升级策略会按当前构建缓存，断网不将它变成可选；从未获取新策略的旧离线客户端无法知道远端的新要求。

这属于新版客户端的兼容性限制，不是服务器安全边界。未包含此功能的旧版本无法被它远程强制更新；需要更严格的服务端版本准入时，应另行设计 API 兼容策略。

## 更新源与校验

已采用现有域名，清单地址为：

- Windows：`https://plan.zijun701.cn/client-updates/windows/latest.json`
- Android：`https://plan.zijun701.cn/client-updates/android/latest.json`

业务服务器地址可以修改，更新源在构建时固定，更新请求不携带业务账号凭据。正式更新源与安装包都必须使用 HTTPS。Android 调试包仅额外允许本机测试 HTTP，不允许校园网任意明文地址。

Windows 通过 Tauri 验证下载包的更新签名；Android 校验大小、SHA256、包名、版本与安装应用的签名，并在交给系统安装前再校验。失败不进入可安装状态，保留现有应用与业务数据。APK 最大 256 MiB，Android 暂不支持签名轮换。更新下载使用独立后台执行队列，不占用业务 API 的执行队列。HTTPS 证书、APK 签名、Tauri 更新签名和 Windows 发布者代码签名是不同的东西。实现依据 [Tauri 更新文档](https://v2.tauri.app/plugin/updater/) 和 [Android 安装权限说明](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())。

当前 EXE 使用本地测试更新密钥，APK 使用调试签名，不能直接面向公众发布。正式发布需单独管理并备份长期签名身份；私钥、keystore 和密码不得加入 Git 或公开下载目录。更换签名身份可能要求用户再次手动安装，务必在公众发布前确定。

## 准备发布目录

先构建两个客户端，再双击根目录 `prepare-client-update-release.bat`。它只准备本地测试目录 `client-update-artifacts/debug`，不上传、不安装。Android 清单生成器核对实际 APK 的包名、版本及签名，避免把旧包写成新版本。

正式构建后运行以下命令，脚本会要求 `android-artifacts/PlanCabin-0.1.1-release.apk`，而不是调试包：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-client-update-release.ps1
```

默认所有更新均可选。需要强制停止旧版本时，由维护者明确设置两个平台的最低版本，且不能高于发布版本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-client-update-release.ps1 `
  -MinimumWindowsVersion 0.1.1 -MinimumAndroidVersionCode 5
```

这将要求 Windows 低于 0.1.1、Android 构建号低于 5 的已有更新客户端升级；不会强迫同版本安装。Android 的构建号每次发布必须递增。正式发布命令本身不证明 Windows 使用了正式私钥，维护者仍需确认签名配置。

生成目录内，两个平台各有 `latest.json` 与 `releases/版本号/` 安装包。未来服务器只静态托管这些文件，与业务 API 分开；先上传安装包与 `.sig`，核对 HTTPS 可访问，最后更新清单。清单应禁用缓存，版本目录不可修改；保留旧包用于诊断，不能用降级覆盖替代业务回滚。本轮没有创建这些线上文件。

## 本地验收与待办

本轮前端 78 项、Android Java 11 项、Rust 原生 9 项（包含本地 HTTPS 集成）、浏览器 28 项和测试清单 4 项，共 130 项测试通过。浏览器包含桌面/手机尺寸的更新桥接模拟，不证明操作系统原生安装成功。两组 EXE 和 APK 构建及 APK 签名验证通过，实际包版本与两组 APK 签名身份一致。HTTPS 功能检查确认可信 CA 可连接，不受信 CA、错误主机名及明文连接被拒绝；测试服务不提供私钥、密码或路径穿越访问。浏览器测试结束时额外清理了本次 43130 预览和 43131 临时验收 API，43132 业务服务保持运行。

2026-10-03 已将 Windows 原安装目录中的客户端覆盖升级至 0.1.1，Android 同签名覆盖升级至 0.1.1（构建号 5），未卸载应用或清除数据。用户确认两端安装版本正确、自动下载默认未勾选、原账号保持登录、计划和任务保留、仍只有星期日休息。用户随后确认两端主动勾选与取消自动下载后，关闭重开均保持对应偏好。这些结果只证明手动引导升级和偏好持久化，不代表应用内下载与安装已经通过。

本地验收使用专用构建和独立的 HTTPS 发布源 `https://127.0.0.1:43133`。测试起点为 0.1.1（Android 构建号 5），更新目标为 0.1.2（构建号 6）；两者使用现有测试签名身份，不卸载或清除客户端数据。业务服务器仍为原来的 `http://127.0.0.1:43132`。正式更新源和阿里云服务不变。

2026-10-03 经用户确认，Windows 在原安装目录、Android 以同签名覆盖安装了本地验收起点包。安装元数据均为 0.1.1，Android 构建号 5，43132 与 43133 的 USB 转发已恢复。用户确认手机正常，PC 检查更新报 `builder error`。定位为 Windows 构建脚本导入 `vcvars` 环境时，将多行公开测试 CA 从 1492 字符截断为 27 字符的 BEGIN 标记。已将证书加载移至环境导入之后，增加完整 PEM 构建保护及错误原因显示；原生回归测试同时检查编译时嵌入的证书，避免仅测试运行时从磁盘加载的证书。本次 Rust 10 项、前端 78 项测试通过。修复包在原目录覆盖安装后，核验完整公开 CA 已嵌入客户端；用户确认 PC 检查更新显示“当前已是最新版本”，初始化故障实机复验通过。

随后本地测试源切换为 `optional`，用户确认两端的新版本提示和“稍后处理”正常，并确认下载、校验及应用内安装后均为 0.1.2。只读核验 Windows 原目录 EXE 的产品和文件版本均为 0.1.2，Android 包版本为 0.1.2、构建号 6。手机 USB 转发正常，测试源保持可选更新；没有手动推送目标包或修改阿里云服务。用户随后确认升级后重开时的账号、计划、休息日设置及再次检查无重复更新提示均正常，原有待同步操作检查无问题。由于未记录升级前是否有非空队列及其条数，这不替代非空队列跨版本迁移的专项实机测试。取消安装、强制升级、坏签名、错哈希及下载中断仍待实机验收。

双击根目录 `prepare-local-update-test.bat` 构建两组包，或执行 `scripts/update-lab.ps1 -Action build`。文件分别在 `client-update-artifacts/local/bootstrap` 和 `target`，不会覆盖原来的正式源测试包。准备完成后，执行以下命令管理测试源：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action start
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action scenario -Scenario optional
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action check
```

手机额外需要 USB 转发 `adb reverse tcp:43133 tcp:43133`，同时保留业务 API 的 43132 转发。只有在保存编辑内容并获用户确认后，才覆盖安装 `bootstrap` 包；`target` 包必须通过应用内下载并由用户确认安装，不能手动推送后宣称更新链路通过。Windows 保持原安装目录，Android 必须同签名安装，不得用卸载绕过签名检查。

测试源默认 `idle`（没有更新）。`optional` 为可选更新，`required` 将最低版本提高到目标版本；`bad-hash` 用于 Android 错误哈希，`bad-signature` 用于 Windows 错误更新签名，`interrupted` 模拟下载中断。切换场景前重开客户端，避免已校验的下载缓存影响结果。切回可选场景并成功联网检查后，才解除此前获取的强制策略；仅停止测试源不会解除缓存的必要升级策略。

证书由 JDK keytool 生成，30 天有效，私钥及密码保存在 Git 忽略的 `.desktop-tools/update-lab/tls`，不进入下载目录。只有公开 CA 证书嵌入专用验收包的更新 HTTP 客户端；保留证书、主机名与包签名校验，限制到固定本机 HTTPS 地址，不接受重定向，不影响业务 HTTP 客户端、系统证书信任或 hosts。Android release 构建禁止包含此测试证书。测试源只监听本机，按白名单提供清单与目标安装包，不提供任意目录访问。

测试结束后执行 `scripts/update-lab.ps1 -Action stop`，它只停止经过进程身份检查的测试服务，不停止业务 API。回到正式源需要构建不带测试证书的同签名、更高构建号包，再经用户确认覆盖安装；不要降低 Android 构建号。本地测试版本不应发布到公网。

复测测试源本身可运行 `node scripts/update-lab-smoke.mjs`（结束时恢复 `idle`），原生 TLS 检查可运行 `scripts/desktop.ps1 -Action test -IncludeLabTlsTest`。复测期间不要在客户端点击下载或安装。若重新构建包，应先停止测试源，完成两组构建和清单登记后再启动，以免使用旧清单描述新文件。

1. 普通提示可延后，自动下载默认关闭、主动勾选后保持，下载后不会自行安装。
2. 取消安装后仍能使用原版本；更新成功后账号、计划、休息日与待同步队列保持。
3. 必要升级阻止编辑，但同步队列和导出入口仍可访问。
4. 坏签名、错哈希、错包名、旧构建号、下载中断、系统拒绝安装均不能覆盖现有应用。

验收完成后才确定正式签名并发布云端更新源。当前不能因为公网尚无 `latest.json` 而把检查错误当作业务服务器故障，也不要为此关闭 HTTPS 或签名检查。
