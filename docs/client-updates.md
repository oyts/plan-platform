# 客户端更新与本地验收

0.1.1 为 Windows EXE 与 Android APK 加入更新提示、下载和安装入口。两端已通过应用内更新至本地验收版 0.1.3，完成可选更新、取消安装、异常包拒绝、下载中断、强制升级及断网策略保留验收；手机的非空待同步队列在升级后保留，并在恢复连接后同步成功。后续 0.1.4 长期签名包已完成正式安装、云端连接和会话重开验收；Windows 公网下载及更新源已启用并经正式客户端检查，Android 仅私下分发受邀测试，见 [Windows 发布记录](windows-public-release.md) 与 [受邀客户端测试](invited-client-testing.md)。界面沿用现有白蓝配色，在服务器设置中展示安装版本、发布说明和明确的操作状态。

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

此前验收的 EXE 使用本地测试更新密钥，APK 使用调试签名，不能直接面向公众发布。0.1.4 长期签名候选包已另行构建，Android 使用新包名，与旧包并存；首次 Windows 长期更新身份需要用户确认后手动安装。当前尚未启用公开下载，操作与待办见 [正式客户端签名与 APP 备案准备](formal-client-release.md)。私钥、keystore 和密码不得加入 Git 或公开下载目录。

## 准备发布目录

先构建两个客户端，再双击根目录 `prepare-client-update-release.bat`。它只准备本地测试目录 `client-update-artifacts/debug`，不上传、不安装。Android 清单生成器核对实际 APK 的包名、版本及签名，避免把旧包写成新版本。

正式候选包优先使用 `scripts/prepare-formal-clients.ps1` 的隔离构建与核验流程。当前仅发布 Windows 的准备使用 `scripts/prepare-windows-public-release.ps1`，直接读取已验收候选包并核对独立备份，不生成 Android 发布文件。以下旧目录清单工具用于双端同时准备，要求当前版本的 release APK 与长期签名 EXE，不接受调试 APK 或仅格式合法但签名错误的 EXE；APP 备案尚未完成时不要用它开放双端下载：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-client-update-release.ps1
```

默认所有更新均可选。需要强制停止旧版本时，由维护者明确设置两个平台的最低版本，且不能高于发布版本：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-client-update-release.ps1 `
  -MinimumWindowsVersion 0.1.1 -MinimumAndroidVersionCode 5
```

这将要求使用该更新源的 Windows 低于 0.1.1、Android 构建号低于 5 的客户端升级；不会强迫同版本安装，也不会让旧包名或旧签名自动变成新身份。Android 的构建号每次发布必须递增。清单准备会核验真实长期签名，但不替代安装验收、离线备份与发布许可。

生成目录内，两个平台各有 `latest.json` 与 `releases/版本号/` 安装包。未来服务器只静态托管这些文件，与业务 API 分开；先上传安装包与 `.sig`，核对 HTTPS 可访问，最后更新清单。清单应禁用缓存，版本目录不可修改；保留旧包用于诊断，不能用降级覆盖替代业务回滚。本轮没有创建这些线上文件。

## 本地验收与待办

本轮前端 78 项、Android Java 11 项、Rust 原生 9 项（包含本地 HTTPS 集成）、浏览器 28 项和测试清单 4 项，共 130 项测试通过。浏览器包含桌面/手机尺寸的更新桥接模拟，不证明操作系统原生安装成功。两组 EXE 和 APK 构建及 APK 签名验证通过，实际包版本与两组 APK 签名身份一致。HTTPS 功能检查确认可信 CA 可连接，不受信 CA、错误主机名及明文连接被拒绝；测试服务不提供私钥、密码或路径穿越访问。浏览器测试结束时额外清理了本次 43130 预览和 43131 临时验收 API，43132 业务服务保持运行。

2026-10-03 已将 Windows 原安装目录中的客户端覆盖升级至 0.1.1，Android 同签名覆盖升级至 0.1.1（构建号 5），未卸载应用或清除数据。用户确认两端安装版本正确、自动下载默认未勾选、原账号保持登录、计划和任务保留、仍只有星期日休息。用户随后确认两端主动勾选与取消自动下载后，关闭重开均保持对应偏好。这些结果只证明手动引导升级和偏好持久化，不代表应用内下载与安装已经通过。

本地验收使用专用构建和独立的 HTTPS 发布源 `https://127.0.0.1:43133`。测试起点为 0.1.1（Android 构建号 5），更新目标为 0.1.2（构建号 6）；两者使用现有测试签名身份，不卸载或清除客户端数据。业务服务器仍为原来的 `http://127.0.0.1:43132`。正式更新源和阿里云服务不变。

2026-10-03 经用户确认，Windows 在原安装目录、Android 以同签名覆盖安装了本地验收起点包。安装元数据均为 0.1.1，Android 构建号 5，43132 与 43133 的 USB 转发已恢复。用户确认手机正常，PC 检查更新报 `builder error`。定位为 Windows 构建脚本导入 `vcvars` 环境时，将多行公开测试 CA 从 1492 字符截断为 27 字符的 BEGIN 标记。已将证书加载移至环境导入之后，增加完整 PEM 构建保护及错误原因显示；原生回归测试同时检查编译时嵌入的证书，避免仅测试运行时从磁盘加载的证书。本次 Rust 10 项、前端 78 项测试通过。修复包在原目录覆盖安装后，核验完整公开 CA 已嵌入客户端；用户确认 PC 检查更新显示“当前已是最新版本”，初始化故障实机复验通过。

随后本地测试源切换为 `optional`，用户确认两端的新版本提示和“稍后处理”正常，并确认下载、校验及应用内安装后均为 0.1.2。只读核验 Windows 原目录 EXE 的产品和文件版本均为 0.1.2，Android 包版本为 0.1.2、构建号 6。手机 USB 转发正常；没有手动推送目标包或修改阿里云服务。用户随后确认升级后重开时的账号、计划、休息日设置及再次检查无重复更新提示均正常，原有待同步操作检查无问题。此轮没有记录非空队列条数；后续 0.1.3 安全阶段另行完成手机非空队列与异常更新实机验收，见下文。

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

复测测试源本身可运行 `node scripts/update-lab-smoke.mjs`，原生 TLS 检查可运行 `scripts/desktop.ps1 -Action test -IncludeLabTlsTest`。自安全验收阶段起，smoke 测试创建一次性的本机随机端口 HTTPS 服务，场景只保存在内存中，不改动客户端使用的 43133 服务或场景文件。若重新构建包，应先停止测试源，完成构建和清单登记后再启动，以免使用旧清单描述新文件。

1. 普通提示可延后，自动下载默认关闭、主动勾选后保持，下载后不会自行安装。
2. 取消安装后仍能使用原版本；更新成功后账号、计划、休息日与待同步队列保持。
3. 必要升级阻止编辑，但同步队列和导出入口仍可访问。
4. 坏签名、错哈希、错包名、旧构建号、下载中断、系统拒绝安装均不能覆盖现有应用。

验收完成后才确定正式签名并发布云端更新源。当前不能因为公网尚无 `latest.json` 而把检查错误当作业务服务器故障，也不要为此关闭 HTTPS 或签名检查。

## 0.1.2 → 0.1.3 安全验收阶段

本阶段保留已经验收的 0.1.2（Android 构建号 6）作为起点，仅新建 0.1.3（构建号 7）目标包，不重建或覆盖旧起点包，不向客户端手动推送目标包。两端继续使用现有测试签名、更新源和业务地址；正式源及阿里云服务不变。

双击根目录 `prepare-update-safety-test.bat`，它依次停止经过身份检查的更新测试服务、只构建安全阶段目标并登记清单、以 `idle` 场景启动服务。不停止业务 API，也不安装客户端。等效命令如下：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action stop
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action build -Stage safety
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/update-lab.ps1 -Action start
```

已验收的起点文件仍在 `client-update-artifacts/local/target`，新目标在 `client-update-artifacts/local/safety-target`。不要重新安装旧 `bootstrap` 或用基础阶段脚本降低客户端版本。登记时核对两个 APK 的实际版本、递增构建号和相同签名身份；目标包最终必须通过应用内下载、校验及确认安装。

实机验收按以下顺序进行，每次切换场景前重开客户端，并先关闭自动下载，以便分别记录用户操作：

1. `invalid-artifact`：两端仍为可选更新，但 Windows 清单故意提供无效更新签名，Android 清单故意提供错误 SHA256。下载必须失败，没有安装入口，版本仍为 0.1.2，原数据仍可用。这是刻意制造的异常，不能关闭校验绕过它。
2. `interrupted`：下载被中断；不能安装不完整文件，允许重试，旧应用和业务数据保留。
3. `optional`：正确包下载通过；点击安装后取消确认，不应调用原生安装或更改版本。
4. `required`：最低支持版本为 0.1.3 / 构建号 7。旧客户端暂停业务编辑和提交，保留同步中心、设置、导出与登录入口；已获取的强制策略在断网重开后仍生效。既有非空队列必须记录条数和内容，不因强制策略而被删除或提交。
5. 用户确认安装正确 0.1.3 更新包后，再核对账号、计划、休息日、非空队列及再次检查不重复提示。专门的非空队列验收须有升级前后记录，不能由单元测试或空队列检查替代。

单平台场景 `bad-signature` 和 `bad-hash` 仍保留供诊断，它们不会同时破坏另一平台的包。解除强制策略时切回 `optional` 并成功联网检查；不能只停止服务。手机保留 `adb reverse tcp:43132 tcp:43132` 与 `adb reverse tcp:43133 tcp:43133`，断开后重新建立。

2026-10-03 已构建两端 0.1.3 目标包并通过 APK 签名与版本登记。新增回归覆盖失败下载不可安装、强制策略在下载失败后保持、收到可选策略后解除、写请求与队列重放被阻止而原操作内容不变、读请求仍可用。浏览器桥接模拟与以下原生实机验收分别记录。

本阶段前端 87 项、Android Java 11 项、Rust 原生 10 项、清单 7 项、桌面/手机尺寸浏览器 32 项，共 147 项测试通过；清单测试包含后续新增的一项正确格式篡改签名验证。独立 HTTPS smoke 检查通过，确认两端正常包大小与 SHA256，以及坏签名、错哈希、中断、路径白名单和 TLS 拒绝行为。浏览器首轮因未指定已缓存浏览器目录而无法启动，配置 `PLAYWRIGHT_BROWSERS_PATH=.desktop-tools/playwright` 后全量重跑通过。临时 43130/43131 测试服务已退出，43132 业务服务保持运行。

原生实机验收已完成：PC 最初拒绝不合法 Minisign 编码；随后改为保持算法、密钥 ID、注释和编码长度不变，仅篡改一个签名字节，PC 报 `The signature verification failed`，确认实际签名验证拒绝。手机错误 SHA256 报完整性校验失败。中断传输后，PC 报响应读取失败 / TLS unexpected EOF，手机报连接失败；恢复正确源后，两端成功下载但取消安装，用户确认仍为 0.1.2、原计划可见。手机中断提示过于笼统，可在后续体验阶段细分错误，但不得绕过 TLS 或完整性校验。

随后仅暂停手机 43132 业务转发，保留 43133 更新转发。用户将已缓存任务标题加上“－升级队列测试”，重开后确认 1 项本地操作、0 项冲突。启用 `required` 后，两端均不能进入业务编辑，设置、同步、导出入口保留，手机队列仍为 1 项、0 冲突；再暂停更新服务并重开，两端仍保持强制限制和队列。恢复更新源后，由用户在应用内确认安装，未手动推送目标包或卸载。

只读安装核验显示 Windows 产品和文件版本均为 0.1.3，Android 为 0.1.3 / 构建号 7。用户确认账号、计划和休息日配置保留，限制解除，手机标题与 1 项本地操作、0 项冲突仍在。恢复手机业务转发后，用户确认同步为 0 项操作、0 项冲突，PC 刷新可见新标题，两端再次检查无重复升级提示。非空队列跨版本专项实机验收覆盖手机；PC 的非空队列实机迁移未单独构造，不扩大此结果的范围。

本阶段本地验收结束后，测试源切回 `idle`，保留两端 0.1.3 和业务 / 更新 USB 转发；未改动阿里云线上服务、正式更新源或系统证书信任。后续先搭建原生客户端自动化，再开展手机布局优化。公开发布、正式签名、其他厂商设备、错包名 / 错签名 APK 与系统拒绝安装等额外专项不属于本阶段已完成的实机结果。
