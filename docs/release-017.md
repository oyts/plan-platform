# 0.1.7 受邀应用内更新验收

状态：Web/API、Windows 与 Android 已统一为 0.1.7；Android 构建号 12。真实应用内下载、系统安装和非空队列升级保留验收通过。Windows 安装包公开，Android 仅受邀下载与分享；公开注册、官网 APK 和二维码仍关闭。

此版本用于从已验收的 Android 0.1.6 / 11 执行真正的应用内下载、完整性及签名校验、用户与系统确认安装。沿用原应用身份和证书，不手动推送目标 APK，不卸载或清数据。官网仅发布 Windows，Android 受登录后的 API 保护，公开注册仍关闭。

0.1.6 接入、回归证据及撤回首个错误地址候选的说明见 [接入记录](release-016.md)。

## 已通过的验收

后端 34 项、前端 142 项、Android Java 13 项通过。完整本地 Playwright 回归 74 项通过、6 项按设计跳过；签名与发布工具 21 项通过。

正式 HTTPS 网页回归通过 27 项，涵盖桌面和手机视口的三模式读写、休息日、阶段依赖、离线队列、刷新保留、409 冲突、普通账号权限及跨用户隔离。报告位于本机忽略目录 `acceptance-results/public-web/latest.html`。手机视口测试不等于 APK 真机验收。

Windows 从 0.1.5 在原目录覆盖至 0.1.7，安装后首次启动前 377 个原应用数据文件哈希不变。实际客户端已检查三模式、云端连接、同步中心、版本与关闭重开后的登录保持。队列为空，因此不计作非空队列升级保留验收。官网发布后，客户端实际显示 0.1.7、当前已是最新版本，自动下载未勾选。

Windows 官网五项验收全部通过：API 健康与关闭注册、HTTP 跳转 HTTPS、清单缓存控制、真实下载哈希和 Minisign 签名、桌面/手机视口下载及 Android 二维码禁用。报告为 `acceptance-results/windows-public-cloud/2026-10-13T06-49-43-433Z-11cdaa16/report.json`。初次浏览器检查因运行目录未设置而中止，使用项目现有浏览器复跑通过。

## 部署和安装包

云端部署为 `releases/0.1.7-0418cfdc3f71`，升级前 MySQL 备份 `plan_platform-mysql-20261013-060838.sql.gz` 已核验。无新 Flyway 迁移；旧 Node 容器、镜像、数据库卷与回滚入口保留。

Windows 下载发布目录为 `client-public/releases/windows-017-IbJvt6`，旧安装包原字节保留。回滚命令：

```sh
sh /home/oyts/plan-platform/client-public/releases/windows-017-IbJvt6/publish.sh rollback windows-017-IbJvt6
```

正式候选目录 `production-artifacts/client-release/0.1.7/20261013T060526Z-fea09f5b` 中的安装包：

| 文件 | SHA-256 | 验收状态 |
| --- | --- | --- |
| Plan Cabin_0.1.7_x64-setup.exe | `15654afa13500dcdba69c4199ca733e0f37ba6ebba7f3a58aef654251146901d` | 正式 PC 覆盖安装及官网验证通过 |
| PlanCabin-0.1.7-release.apk | `cb9dffd6db17aca002b2ba61ccb4c3f87f799b7ad8a6d3b6d973797aba281552` | 应用内下载、校验、实际安装及非空队列保留通过；手机已安装 APK 哈希完全匹配 |

Android 构建号 12 已启用登录保护的受限更新清单。云端发布目录为 `android-private/staging/android-12-d24ddd45`，回滚命令：

```sh
sh /home/oyts/plan-platform/android-private/staging/android-12-d24ddd45/rollback.sh
```

0.1.7 的 4 项真实 HTTPS 鉴权与 APK 下载哈希验证通过，报告为 `acceptance-results/invited-android/2026-10-13T11-55-46-471Z-0f80588d/report.json`。公开静态 APK、二维码和注册仍关闭；未用 ADB 手动安装目标正式 APK。

## Android 真机验收

OEM 输入法会重排 ADB 键盘事件，已停止使用该输入方式保存草稿。一次输入探测产生的合成待办 `0261013－1420NU` 留在测试现场，真实待办未编辑。独立测试辅助包现已成功安装，原生无障碍精确输入及空搜索已在正式 APP 验证；窗口服务启动等待修正后，待办弹窗输入也通过。输入仅限正式应用前台的合成待办标题和搜索字段；不读取会话、不操作密码、不启用正式应用调试。Python 输入保护 9 项测试通过。

正式 APP 自动化已创建合成待办 `nativeupgrade-20261013-1510`，基线为 0 项操作、0 项冲突；断网后改为 `nativeupgrade-20261013-1510-offline` 并保存、关闭重开。离线标题仍在，同步中心准确显示 1 项操作、0 项冲突。Wi-Fi 和移动数据开关均从原状态 1 暂时关闭，最终恢复为 1。安装之前未提交或清除队列。

恢复连接后，APP 检测到可选 0.1.7，自动下载保持关闭；点击“稍后处理”后仍可进入待办，离线标题保留。随后从 APP 主动下载，原生哈希、包名、版本及原签名检查通过，页面显示“更新包已校验”。首次请求安装提示需授权来源，用户亲自完成后，再由 APP 将文件交给系统安装器。

手机“超级守护”曾提示“未核准（备案）问题”并管控安装。用户自行处理系统提示并确认安装后，实际读取正式包为 0.1.7 / 12，已安装 `base.apk` 的 SHA-256 与上表完全匹配。没有关闭安全保护、自动绕过管控或以 ADB 覆盖安装替代应用内链路。

升级后的自动验收结果为 PASS：

- 无需重新登录，独立离线待办标题保留；队列仍为 1 项操作、0 项冲突。
- 关闭并重新打开正式 APP 后，仍保持会话、标题和 1/0 队列。
- 周期计划、目标路线、今日待办均能加载，未出现连接错误；未编辑真实业务内容。
- 从同步中心展开唯一请求并验证其标题属于合成待办后，才提交这一项操作。同步成功，队列变为 0 项操作、0 项冲突，待办页重新加载后标题仍在。
- 主动检查更新显示“当前已是最新版本”，安装版本 0.1.7 / 构建 12，自动下载未勾选。

验收工具为 `scripts/android-post-upgrade-acceptance.py`。设备为 V2426A、Android 15；不将单机结果推广为所有机型安装表现。手机被切换到其他应用时自动测试曾暂停；一份未捕获到计划舱的诊断截图已删除，未作为证据。前台检测已加强为实际聚焦窗口，并在读取与截图后再次核对。

电脑当时被其他程序占用，已停止 PC 界面输入。本轮这条合成待办的 PC 同屏对照尚未做，不影响已完成的 Windows 升级验收与网页三模式/隔离回归，但不能把手机同步成功称为本轮 PC 实机跨端对照通过。原休息日的每个字段没有在这次原生升级后逐一比对；未实施数据迁移或编辑这些配置。

Windows 发布后，受限 Android 接口再次通过 4 项真实 HTTPS 验证，两个普通验收账号下载的 0.1.6 / 11 APK 哈希匹配，未登录下载拒绝且公开 APK 不可访问。报告为 `acceptance-results/invited-android/2026-10-13T06-57-06-642Z-ad25fca4/report.json`。

## 回归命令与边界

实际版本与界面检查命令：

```powershell
$testDeviceSerial = 'YOUR_DEVICE_SERIAL'
& .desktop-tools/android-sdk/platform-tools/adb.exe -s $testDeviceSerial shell dumpsys package cn.zijun701.plan.app
& .desktop-tools/public-web/venv/Scripts/python.exe scripts/android-ui-acceptance.py --serial $testDeviceSerial inspect
```

本次合成队列已同步，不要直接重跑要求初始 1/0 队列的升级驱动，也不要为了重跑清除账号或制造真实任务冲突。下一轮应使用新的独立记录和明确的升级起止版本。目标更新已发布，不重跑同一个 staging 的发布命令。

驱动每次重新观察真实界面，遇到锁屏、登录页或外部系统页面就停止。可重用的 `android-offline-upgrade-baseline.py` 只操作已打开的合成待办，自动恢复原网络开关；已成功运行的基线不要再次执行。GitHub 发布范围仅为 Windows EXE、签名与哈希，不上传 APK、私密测试数据或密钥。可选/必更、账号切换和签名失败分支由既有单元与更新实验室回归覆盖，本次未在正式全体受邀用户更新源上制造强制更新或损坏 APK。

## GitHub 发布

[v0.1.7 Release](https://github.com/oyts/plan-platform/releases/tag/v0.1.7) 已正式发布，标签对应提交 `eee6b6211ef83e7692f96bbaee4aa4da3d2283d3`。公开附件仅有 Windows EXE、对应 Minisign 签名和 `SHA256SUMS.txt`，没有 APK；不移动该标签或替换已发布安装包。

可私下分享的本机 APK 位于 `production-artifacts/client-release/0.1.7/20261013T060526Z-fea09f5b/android/PlanCabin-0.1.7-release.apk`，另有相同哈希副本位于受限发布 staging。首次安装者仍需自己的受邀账号；不要与密码、邀请链接或签名资料打包分享。
