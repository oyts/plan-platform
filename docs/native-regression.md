# PC 与 Android 真机自动化回归

本阶段为真实 Tauri EXE 和 Android 手机 APK 建立可重复运行的业务回归。使用 Playwright 连接两端的 Chromium WebView 调试协议，驱动实际界面；请求仍经过 Rust 或 Java 原生桥接、Windows 凭据管理器或 Android Keystore，不替换原生调用，不以普通浏览器模拟结果冒充真机通过。

测试使用独立客户端、随机合成账号和临时 H2 内存数据库。现有计划舱客户端、账号、计划、离线队列及阿里云服务不在测试修改范围。

## 一键运行

首次使用或代码变化后双击根目录 `prepare-native-regression.bat`，构建独立的调试客户端。构建需要 Java 21、Node.js 22、Android SDK 36、Rust MSVC 和已安装的前端依赖；后端 JAR 需先在 `backend` 执行 `mvn -Dplan.build.directory=target-acceptance package` 生成。验收启动使用独立 JAR 快照，不覆盖原服务使用的默认 `target` 文件。

将 Android 手机用 USB 连接并解锁，开启 USB 调试、授权电脑，然后双击 `verify-native-clients.bat`。首次安装手机会出现“计划舱回归测试”，请按系统提示确认安装。它与原计划舱并存，不卸载、不覆盖原包；后续仅同签名覆盖这个测试包。

运行结束查看 `acceptance-results/native/latest.html`，每个场景有 PASS 或 FAIL、耗时和截图。每次运行还保留独立的 `report.json`、API 日志和截图目录；`latest.junit.xml` 可供测试平台读取。报告和产物已被 Git 忽略，截图只应包含合成测试数据。

命令行可先检查环境，或者只测一个平台：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action check
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action run -Platform windows
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action run -Platform android -Serial 手机序列号
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action test
```

默认是 `both`，没有授权手机或存在多台未指定的手机会失败退出，不会自动跳过 Android 后宣称双端通过。单平台结果只代表该平台；单平台冲突测试由另一个真实 API 会话提交远端版本，不称为 PC 与手机互通。

## 自动化场景

1. 真实原生桥接、服务器测试与配置、两端登录。
2. 界面创建计划和任务，其他客户端可见；没有勾选休息日时不自动标记周末。
3. 二次编辑为星期日休息，两端日程只有星期日为休息日，星期六为计划日，任务保留。
4. 手机底部导航可用、日程任务打开详情面板、关闭后能通过“更多”进入同步；PC 不显示手机主导航。
5. 切换周期计划与目标路线，各自保留位置；真实 TCP 断连并终止重开后仍回到路线，计划任务不受影响。
6. 阶段折叠、步骤编辑回显和前置条件互通；真实断网编辑路线步骤，重开保留，恢复后同步且组织信息不丢失。
7. 今日待办独立模式下新增、离线编辑、重开保留与幂等同步，不混入周期计划和目标路线。
8. 终止并重开真实客户端，验证原生会话、计划和配置恢复。
9. 切断测试 TCP 转发，两端分别编辑不同任务；重开后修改可见，并各保留 1 项本地操作、0 项冲突。此时服务器仍是原标题。
10. 恢复转发、重放真实队列，两端刷新互通，没有重复任务。
11. 手机离线提交两个修改，PC 修改同一任务；恢复后队首显示冲突，后续操作仍为等待、尝试次数为 0，远端标题不被覆盖。保留这两项合成队列供失败诊断，不自动放弃冲突。
12. 退出并重开不自动登录原账号；切换合成账号后原计划和待同步队列不可见；双端模式还确认另一端仍登录。

本阶段不自动化系统安装确认、分享目标选择器、权限授权、正式证书与签名更新、跨版本升级或其他厂商手机兼容性。这些需要专门的安装测试和人工验收，不能由当前业务报告推导为通过。原客户端 0.1.3 的更新验收结果另见 [客户端更新](client-updates.md)。独立回归包沿用源码调试版本号，不是正式版本降级。

## 本轮验收记录（2026-10-03）

Windows 真实 EXE 与 iQOO V2426A（Android 15）完成连续三轮双端运行，8 项业务场景均通过。最后一轮记录为 `acceptance-results/native/2026-10-03T14-50-41-331Z-8f32b3a6/report.json`，每项保留 PC 与手机截图，JUnit 为 8 项、0 失败。

辅助测试结果：回归框架 6 项、前端 87 项、Java 11 项、Rust 9 项通过。Rust 的 1 项本地 HTTPS 更新实验测试在默认命令中按设计忽略，未计为通过；本阶段没有新增升级安装自动化结论。

安全负向检查：Android 的 `:app:assemble --dry-run` 聚合任务包含 release 时被明确拒绝，未生成 release 产物。

清理检查确认 43134—43138 无监听，回归锁与调试转发已移除，原 43132 业务服务健康为 UP、43133 更新实验服务及原 USB 反向转发保留。正式 PC 与手机安装版本仍为 0.1.3；独立回归包保留以便后续重跑，未卸载或清除正式应用。

## Android 新版真机验收

2026 年 10 月 12 日，iQOO V2426A（Android 15）使用独立 0.1.4 APK（构建号 8）完成 **12 项通过、0 失败** 的单平台业务回归，记录为 `acceptance-results/native/2026-10-12T04-41-23-713Z-f04bb5d7/report.json`。涵盖三模式、休息日、路线阶段和依赖、待办离线编辑、原生会话重开、顺序同步、冲突停靠与账号隔离。

本次运行使用真实 Android WebView 和 Java 桥接，业务服务为隔离的临时 H2 API。单平台的远端修改由另一合法 API 会话提交，不算作本轮 PC 与手机互通验收。回归框架自身 6 项测试也通过。

另行通过手机 Java 原生请求，对正式域名 `https://plan.zijun701.cn` 执行“测试连接”：真实 HTTPS 账号配置接口检查通过，没有设置 USB 业务反向转发，也没有保存新服务器配置或登录真实账号。它证明手机能直连新版云端账号接口，不代表云端完整登录和业务回归。检查脚本为 `scripts/android-public-connection.mjs`，可在完整回归结束、独立测试包已关闭后运行：

```powershell
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/android-public-connection.mjs
```

连接报告与截图在被 Git 忽略的 `acceptance-results/android-public/`。测试只启动已安装的独立回归包，检查其 0.1.4 / 构建 8 身份；不安装或清理正式包，不保存配置，不关闭 TLS 检查。结束后移除本轮持有的 CDP 转发并停止测试包。

正式计划舱测试前后均保持 **0.1.3 / 构建 7**，没有覆盖或清理它的账号、计划及队列。回归专用端口 43134—43138 无残留监听，ADB 调试和业务反向转发、回归锁均已移除。独立 debug APK 的真机通过不等于正式签名包已发布，公开下载与正式升级仍待签名和安装专项验收。

## 隔离与安全保护

后续手机布局阶段增加第 4 项，工作区阶段增加第 5 项，路线阶段增加第 6 项，今日待办阶段再增加 1 项，当前共 12 项。对应记录见 [手机布局优化](mobile-layout.md)、[工作区模式](workspace-modes.md)、[路线阶段](route-stages.md) 与 [官网和待办](product-website-and-todos.md)。上文 8 项记录保留为自动化框架首次交付时的历史结果。

| 资源 | 回归专用值 |
| --- | --- |
| PC 标识 | `cn.zijun701.plan.desktop.regression` |
| Android 包名 | `cn.zijun701.plan.mobile.regression` |
| Windows 凭据服务 | `PlanCabin.Regression.RefreshSession` |
| 临时内存 API | `127.0.0.1:43134` |
| PC WebView 调试 | `127.0.0.1:43135` |
| Android USB 调试转发 | `127.0.0.1:43136` |
| PC 测试故障转发 | `127.0.0.1:43137` |
| 手机测试故障转发 | `127.0.0.1:43138` |

运行前检查产物与 API JAR 的 SHA256、客户端源码指纹、Android 包名、端口占用和 ADB 转发占用；源码变动后必须重新构建，检测到冲突即退出，不停止占用者。PC 使用独立 WebView 数据目录，回归调试功能由 Cargo feature 控制，release 构建拒绝启用。Android 回归包只允许 debug 构建，关闭更新源，不下载线上安装包。

离线注入只操作回归专用 TCP 服务。没有使用浏览器 `setOffline` 或接口拦截，因为它们不能可靠切断 Java/Rust 原生请求。清理只停止本轮持有的进程、回归 APK 和转发，保留测试截图、测试包及合成客户端数据；不碰 43132/43133 原业务和更新服务，不修改系统证书或防火墙。

调试端口仅供本机开发测试。不要用回归包登录真实账号，也不要把回归包作为公开安装包发布。连接 USB 的电脑拥有调试权限，测试结束会移除本轮建立的调试转发。端口空闲检查和最终绑定之间仍存在操作系统级竞争；启动失败会作为失败记录，不复用其他进程。

## 常见问题

- 手机安装被拒绝：按手机提示允许安装回归测试包后重新运行，不卸载原计划舱。安装确认最多等待 180 秒，其他 ADB 操作仍保留较短超时。
- 手机未授权或已锁屏：解锁并授权 USB 调试后重新运行。
- 调试端口已占用：确认占用者身份；关闭自己启动的回归进程后重试，不使用批量杀进程命令。
- 构建后校验不一致：重新运行构建入口，不手工替换 EXE/APK 或修改 `build.json`。
- 报告失败：先看第一个 FAIL 及对应截图。前置检查失败不是功能失败，也不是测试通过。
- 窗口意外关闭或 USB 断开：本轮应失败；先恢复连接，再完整重跑。不把中断前部分 PASS 当作整轮成功。
- 不要强制关闭测试终端：操作系统强行终止脚本时无法保证执行清理。若遗留 `run.lock` 或测试端口，先按 PID 核对本轮进程并处理，勿批量终止 Java、ADB 或计划舱。

技术依据：[Playwright CDP](https://playwright.dev/docs/api/class-browsertype#browser-type-connect-over-cdp)、[Chrome WebView 调试](https://developer.chrome.com/docs/devtools/remote-debugging/webviews)、[Tauri 测试](https://v2.tauri.app/develop/tests/webdriver/)。CDP 的能力低于 Playwright 自带协议，因此本轮不依赖录制视频、完整 trace 或浏览器下载事件作为原生系统行为的证据。
