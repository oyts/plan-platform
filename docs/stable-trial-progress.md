# 试用收口阶段续做记录

2026年10月21日开始阶段六，基于源码0ac1ba8和正式三端0.1.18。阶段五模板源码已完成，尚未发布。本轮范围为试用收口、模板教学、官网与功能状态整理、PC导出及合成非空队列升级验收，并准备稳定候选。现已完成独立PC六组定向验收，不等于正式包覆盖通过。Android随后已非流式安装成功，核心三组通过，导出文件内容已核对；分享取消返回及系统文件选择器仍未完成，见文末。未验收APK不推送。

候选源码统一为0.1.19／Android24，正式三端仍0.1.18／23。本轮不宣布1.0，不切换生产或开放注册／官网APK。正式PC覆盖仍需保存关闭确认；测试使用独立回归包和合成账号，不修改真实计划。额度剩余10%时保存并直接通知，不安排自动任务。

## 共享实现与验收

已实现第六节模板四步练习并保留原五课进度；官网补草稿箱、提醒、复盘及有能力声明才展示的模板说明；帮助／架构／API／功能对等与求职路线已更新。新客户端连接旧服务器会给整套模板兼容说明，不请求不存在的接口。另修正文案，把可离线入队的待办勾选与需联网的AI／计时分开，不修改底层队列规则。

集中验收：后端11、最终前端28、浏览器28个不同用例、原生夹具9通过；章节编号／能力超时／最终离线说明的4个网页补验通过，不能加算成32个不同用例。证据在 `acceptance-results/stable-trial/`，截图已人工查看。最终API SHA256为 `b77b2e0970b37225dce66cfdb776fa23aeaf363c4e356811ec9a0dc1883c91b2`，归档复用了相同字节，没有再重建冒充已验收程序。

## PC与数据库检查点

独立旧Windows安装为0.1.17，目录 `.desktop-tools/reminder-windows-installed`，已观察原合成账号离线队列0/0，没有清理。已通过应用设置切换本轮隔离服务 `http://127.0.0.1:43163`；新合成夹具在 `acceptance-results/stable-trial/pc/2026-10-21T05-12-54-605Z`，凭据仅在该目录的 `synthetic-login.txt`，不能公开。后台会话服务在本机43162，代理43163；控制命令为 `offline`、`online`、`status`、`stop`。用户需亲自登录一次，之后自动执行合成非空队列、同身份独立安装及导出验收。正式账号与客户端不动。

最终独立0.1.19 NSIS测试包在 `.desktop-tools/stable-trial-installer/d99026d01b7f48a5baa84baeda9e8874/PlanCabin-Stable-Trial-setup.exe`，尚未安装。首个目录759057保留，只能当较早源码。计划用实际独立0.1.17→0.1.19安装验证非空队列，不能写成正式0.1.18→0.1.19已保留。

最终API的独立MySQL目录 `/home/oyts/plan-platform/template-candidates/20261021T051800Z-b77b2e09/report.json` 为PASS：V27→V28、46张原业务表不变、两个时区的9:30、实际旧0.1.18镜像兼容及49表整库恢复均通过。合成库 `template_acceptance_b77b2e09` 与恢复库 `template_restore_b77b2e09` 及备份保留，测试容器停止。生产仍0.1.18且注册关闭。

## 已签名但未发布的正式候选

目录 `production-artifacts/client-release/0.1.19/20261021T052104Z-c686c3c2`。Windows安装包 `windows/Plan Cabin_0.1.19_x64-setup.exe` 的真实更新签名已验证，SHA256为 `0b9c080bc830af187efb8f63aeec51b6a1f29a62e00470e0ffe4befa5cd39525`；没有提供Authenticode发布者证书。Android包 `android/PlanCabin-0.1.19-release.apk` 保持正式包名、原证书、非debug、禁止明文连接和登录受限更新源，构建24，SHA256为 `488216b7136f89ee9c3d17fda54416084f40c9fd4b7e56ecc8681b516c137491`。这些是静态及签名结果，不是安装或手机真机通过，不发朋友或推更新源。

本地部署归档 `production-artifacts/plan-platform-0.1.19.tar.gz`，SHA256为 `3293e8c573a7397337817007bd9dc3cc0fdfa4f99517efd06e60b80ce1f03ada`，显式清单未包含凭据、数据库、测试资料或私钥，没有上传或切换生产。

## 剩余门槛

1. 独立Windows六组已通过，见下方最终结果；正式客户端不动，正式0.1.18→0.1.19覆盖仍需另行确认，不能用独立0.1.17→0.1.19的结果替代。
2. Android非流式重试安装成功，独立0.1.19／24的核心三组已通过，文件生成已核对；分享取消返回／系统文件选择器在窗口焦点变化时停止，不能算完成。正式手机仍0.1.18／23。正式同签名覆盖、生产升级及更新源发布仍须对应门槛。
3. 初次源码已推送2f436fb，服务恢复记录a52c752；本轮新增验收工具继续经审查同步，不上传产物、凭据、会话、数据库、截图或备份正文。

若本机隔离服务因一小时保护到期而停止，用相同已验收JAR续做，不重新注册账号：

```powershell
.\.desktop-tools\node22\node-v22.23.3-win-x64\node.exe scripts/stable-pc-fixture.mjs --resume 'E:\Ai\假期计划\plan-platform\acceptance-results\stable-trial\pc\2026-10-21T05-12-54-605Z'
```

夹具续做分支于2026年10月21日17:17（北京时间）实际启动通过。用户反馈连接失败时，上轮服务已超过一小时保护期，原端口未占用；同JAR、同目录恢复后，43162直连及43163代理的 `/api/health` 均返回0.1.19／ok=true。原数据库和合成凭据文件未重建，恢复证据为该私有目录的 `resume-1792574261391.json`。这仅证明服务恢复，不代表PC已登录或升级保留通过。恢复后的服务同样有一小时保护期；正在运行时不能重复启动。

## 双端补验准备（2026年10月21日）

初次恢复的非交互会话无法接收离线指令；已先核对进程命令行只属于该合成目录，再停止合成服务，以交互终端恢复，原H2和账号未重建。Windows停止进程命令内部错误和后续精确结束结果保留在工具记录。40342随后到期，正式补验使用22815恢复、切离线／联网／最后stop正常结束。需要续做时必须用相同resume命令并启用交互终端启动，不能注册新账号替代已有数据。

独立安装工具 `scripts/install-stable-trial-windows.ps1` 在用户确认后已执行，固定为测试目录／测试标识，备份并校验原回归数据，不读取Windows凭据管理器。529个原数据文件安装前后相同，备份只留本机受限目录。安装器SHA256为 `a53bc5eca1b5a5f161f188433cf469a6d7bfaaedacae53419cc000ddab89605a`。下列为历史实际命令，不需要再次安装：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-stable-trial-windows.ps1 -InstallerSha256 a53bc5eca1b5a5f161f188433cf469a6d7bfaaedacae53419cc000ddab89605a -ConfirmedClosed
```

Android首次沙箱构建遇到原用户Gradle缓存AccessDenied，失败日志 `acceptance-results/stable-trial/native-build-final.log` 保留；没有放宽权限。以原用户权限重建成功，日志 `native-build-owner.log`。九项原生夹具测试通过，预检未安装或启动任何手机应用。新增 `--stable-trial` 严格仅允许Android，四组用例尚未运行；系统文件导入选择器、外部分享目的地和正式包升级仍不是这四组可推导的结果。确认允许安装独立测试包后执行：

```powershell
$env:JAVA_HOME='E:/biancheng/JDK21'
.\.desktop-tools\node22\node-v22.23.3-win-x64\node.exe scripts/native-regression.mjs --platform android --stable-trial
```

这使用新合成账号和隔离43134／43138，不用PC的非空队列，不跑旧业务全量，也不调用付费AI或真实邮件。系统安装确认仍由用户操作。

## Windows最终定向结果

独立回归身份0.1.17→0.1.19共六组通过：实际NSIS覆盖及529文件不变；原合成登录、计划、星期日休息和1/0队列跨升级、离线冷重开保留；恢复联网后明确同步到0/0且原任务ID／唯一数量及新标题由API核对；真实Rust模板结构预览（9:30）和确认保存；实际私有JSON下载到Windows默认Downloads且内容核对；第六课列表和练习入口可用。原生导出文件3848字节、schema4、含1个合成任务和1份模板，SHA256 `17d40f06ec4407b5c61d248822191de042b16f7bf7c765ef988de2376f957daf`。没有出现另存为对话框，所以不声称用户自选保存位置通过。PC未运行完整四步教学或模板应用新副本分支，这些由共享源码证据与后续Android定向验收分别记录。

证据在本轮PC私有目录：`independent-installation.json`、`pc-ui-observations.json`、`server-verification.json`、`windows-export-verification.json`。服务器核对工具只登录该本机合成账号，不代填Windows认证窗口；最初对不存在的单计划GET导致核对工具失败，修正为现有计划列表接口后通过，不是产品故障。UI几次读取到未渲染完成的旧树／零计数，已重新观察实际完成状态；一次窗口几何错误按技能刷新恢复，未用CDP或旁路操纵Windows。测试窗口和本机PC夹具现已正常结束，正式客户端及真实数据未改。

Android失败报告：`acceptance-results/native/2026-10-21T10-46-30-188Z-af70bada/report.json`，cases为空，不能计作通过。重跑工具会先验证实际已安装APK哈希，相同则不重装；需要重装时仅稳定候选分支使用非流式安装，不跳过系统确认或安全提示。

## Android核心业务与文件补测最新状态

用户明确重试后，`adb install --no-streaming -r` 返回Success，独立0.1.19／24的实际APK哈希为 `d985dfc200685f042dfe7deca852e04264e2d2bb80659cc770b9916c27647d35`；正式应用保持0.1.18／23。`2026-10-21T12-26-02-410Z-62c94efc/report.json` 的前三组证明真实Java接口、模板保存与独立副本、9:30和零执行历史、教学保存／进程重开及真实数据不变。第四组证明导出包含本次模板且分享面板能打开，但早期只发出Back并读取后台页面，未验证焦点已回到测试包，不能据其PASS声称分享取消返回通过。已查看实际真机教学完成截图。

最初两次失败分别是验收脚本把合法 `09:30:00` 当作错误、把任务的 `progress.status` 误读为顶层status；对照实际DTO修正，仍严格检查9:30、PENDING、用时0、无开始／完成时间。没有因此修改产品业务或重打包。所有失败报告保留，不把重跑加算为更多不同用例。

系统文件分支增加严格前台检查：只在实际系统分享窗口输入，系统选择器只点击观察到的唯一合成文件／下载目录，不猜坐标、不填认证、不改系统权限。一次前台转入其他应用后即停止；后来观察到vivo `com.vivo.cipherchain` 系统浮层，用户自行处理。再次补测仍在“分享窗口不再持有焦点”时停止，未继续输入；最新失败报告为 `2026-10-21T12-38-06-016Z-681549dd/report.json`。停止后再核对时测试包已在前台、该系统浮层已不在前台，因此不认定手机永久受限或产品有确定故障。系统导入选择器尚未实际运行，外部分享目的地也未验收，没有向第三方应用自动发送文件。

仅文件两组补测使用 `--stable-files`，经API准备新合成夹具，不再重复模板／教学三组核心UI。启动前与接管前均要求用户已置前独立测试包；中途焦点变化会停止。用户准备好且额度可核对后续做：

```powershell
$env:JAVA_HOME='E:/biancheng/JDK21'
.\.desktop-tools\node22\node-v22.23.3-win-x64\node.exe scripts/native-regression.mjs --platform android --stable-files
```

原生夹具防误用测试现10项通过。额度接口最新查询和一次重试均不可用，最后可靠300分钟读数34%已用；本轮保守保存，不声称额度耗尽、不再启动新测试、不启用自动任务。正式服务、正式PC和手机、下载与更新源均未改变。
