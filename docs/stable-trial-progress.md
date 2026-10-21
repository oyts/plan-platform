# 试用收口阶段续做记录

2026年10月21日开始阶段六，基于源码0ac1ba8和正式三端0.1.18。阶段五模板源码已完成，尚未发布。本轮范围为试用收口、模板教学、官网与功能状态整理、PC导出及合成非空队列升级验收，并准备稳定候选；当前PC原生验收尚未完成，不能把本段范围写成结果。Android真机按维护者要求延期，未验收APK不推送。

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

1. PC用户已自行登录，已通过实际界面断开隔离代理、修改合成任务并确认1项本地操作／0项冲突。独立0.1.17窗口正常关闭，代理仍离线，没有提交队列；等待用户确认安装并打开独立0.1.19后继续跨升级保留和导出验收。尚未运行安装器，正式客户端不动。
2. 用户随后明确连接手机，延期状态已解除，先准备独立测试包：0.1.19构建、源码／API／APK哈希及设备预检通过；正式手机仍0.1.18／23。等待独立包安装确认，再运行新增模板／第六课／重开／私有备份分享取消四组用例，不能把预检当真机通过。正式同签名覆盖、生产升级及更新源发布仍须对应门槛。
3. 初次源码已推送2f436fb，服务恢复记录a52c752；本轮新增验收工具继续经审查同步，不上传产物、凭据、会话、数据库、截图或备份正文。

若本机隔离服务因一小时保护到期而停止，用相同已验收JAR续做，不重新注册账号：

```powershell
.\.desktop-tools\node22\node-v22.23.3-win-x64\node.exe scripts/stable-pc-fixture.mjs --resume 'E:\Ai\假期计划\plan-platform\acceptance-results\stable-trial\pc\2026-10-21T05-12-54-605Z'
```

夹具续做分支于2026年10月21日17:17（北京时间）实际启动通过。用户反馈连接失败时，上轮服务已超过一小时保护期，原端口未占用；同JAR、同目录恢复后，43162直连及43163代理的 `/api/health` 均返回0.1.19／ok=true。原数据库和合成凭据文件未重建，恢复证据为该私有目录的 `resume-1792574261391.json`。这仅证明服务恢复，不代表PC已登录或升级保留通过。恢复后的服务同样有一小时保护期；正在运行时不能重复启动。

## 双端补验准备（2026年10月21日）

初次恢复的非交互会话无法接收离线指令；已先核对进程命令行只属于该合成目录，再停止合成服务，以交互终端恢复，原H2和账号未重建。Windows停止进程命令内部错误和后续精确结束结果保留在工具记录。当前控制会话40342；支持 `offline`／`online`／`stop`，当前offline。之后若保护期到期，必须用上方相同resume命令并启用交互终端启动，不能注册新账号来替代已有数据。

独立安装工具 `scripts/install-stable-trial-windows.ps1` 已语法检查但未执行，固定为测试目录／测试标识，备份并校验原回归数据，不读取Windows凭据管理器。安装器SHA256为 `a53bc5eca1b5a5f161f188433cf469a6d7bfaaedacae53419cc000ddab89605a`。用户确认后运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/install-stable-trial-windows.ps1 -InstallerSha256 a53bc5eca1b5a5f161f188433cf469a6d7bfaaedacae53419cc000ddab89605a -ConfirmedClosed
```

Android首次沙箱构建遇到原用户Gradle缓存AccessDenied，失败日志 `acceptance-results/stable-trial/native-build-final.log` 保留；没有放宽权限。以原用户权限重建成功，日志 `native-build-owner.log`。九项原生夹具测试通过，预检未安装或启动任何手机应用。新增 `--stable-trial` 严格仅允许Android，四组用例尚未运行；系统文件导入选择器、外部分享目的地和正式包升级仍不是这四组可推导的结果。确认允许安装独立测试包后执行：

```powershell
$env:JAVA_HOME='E:/biancheng/JDK21'
.\.desktop-tools\node22\node-v22.23.3-win-x64\node.exe scripts/native-regression.mjs --platform android --stable-trial
```

这使用新合成账号和隔离43134／43138，不用PC的非空队列，不跑旧业务全量，也不调用付费AI或真实邮件。系统安装确认仍由用户操作。
