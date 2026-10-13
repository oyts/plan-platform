# 0.1.7 受邀应用内更新验收

状态：Web/API、Windows 正式安装与官网更新源已上线 0.1.7；Android 0.1.6 / 11 → 0.1.7 / 12 应用内升级仍待真机验收，最终 GitHub Release 尚未发布。

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
| PlanCabin-0.1.7-release.apk | `cb9dffd6db17aca002b2ba61ccb4c3f87f799b7ad8a6d3b6d973797aba281552` | 原签名、包名与受限更新 URL 已核验；APP 下载校验通过，系统安装待确认 |

Android 构建号 12 已启用登录保护的受限更新清单。云端发布目录为 `android-private/staging/android-12-d24ddd45`，回滚命令：

```sh
sh /home/oyts/plan-platform/android-private/staging/android-12-d24ddd45/rollback.sh
```

0.1.7 的 4 项真实 HTTPS 鉴权与 APK 下载哈希验证通过，报告为 `acceptance-results/invited-android/2026-10-13T11-55-46-471Z-0f80588d/report.json`。公开静态 APK、二维码和注册仍关闭；未用 ADB 手动安装目标正式 APK。

## Android 剩余验收

OEM 输入法会重排 ADB 键盘事件，已停止使用该输入方式保存草稿。一次输入探测产生的合成待办 `0261013－1420NU` 留在测试现场，真实待办未编辑。独立测试辅助包现已成功安装，原生无障碍精确输入及空搜索已在正式 APP 验证；窗口服务启动等待修正后，待办弹窗输入也通过。输入仅限正式应用前台的合成待办标题和搜索字段；不读取会话、不操作密码、不启用正式应用调试。Python 输入保护 9 项测试通过。

正式 APP 自动化已创建合成待办 `nativeupgrade-20261013-1510`，基线为 0 项操作、0 项冲突；断网后改为 `nativeupgrade-20261013-1510-offline` 并保存、关闭重开。离线标题仍在，同步中心准确显示 1 项操作、0 项冲突。Wi-Fi 和移动数据开关均从原状态 1 暂时关闭，最终恢复为 1。该操作尚未提交，也未清除队列。

恢复连接后，APP 检测到可选 0.1.7，自动下载保持关闭；点击“稍后处理”后仍可进入待办，离线标题保留。随后从 APP 主动下载，原生哈希、包名、版本及原签名检查通过，页面显示“更新包已校验”。首次请求安装提示需授权来源，用户亲自完成后，再由 APP 将文件交给系统安装器。

目前系统安装器显示目标“计划舱 0.1.7”，但正式应用仍为 0.1.6 / 11。手机“超级守护”明确提示应用存在“未核准（备案）问题”，管控本次安装。未关闭安全保护或自动跳过此提示；等待用户自行决定。不能计作安装成功或跨版本队列保留通过。电脑当时被其他程序占用，已停止 PC 界面输入，尚未完成本次合成待办的跨端对照。

Windows 发布后，受限 Android 接口再次通过 4 项真实 HTTPS 验证，两个普通验收账号下载的 0.1.6 / 11 APK 哈希匹配，未登录下载拒绝且公开 APK 不可访问。报告为 `acceptance-results/invited-android/2026-10-13T06-57-06-642Z-ad25fca4/report.json`。

## 续做命令

系统安装完成后先读取实际正式版本，再重新观察前台界面，不重复覆盖或重建离线队列：

```powershell
& .desktop-tools/android-sdk/platform-tools/adb.exe -s 10AF7328GX0052H shell dumpsys package cn.zijun701.plan.app
& .desktop-tools/public-web/venv/Scripts/python.exe scripts/android-ui-acceptance.py --serial 10AF7328GX0052H inspect
```

预期为 0.1.7 / 构建 12，原登录和三模式可用，合成离线标题及 1 项操作、0 项冲突保留。关闭重开后再验证一次，然后仅同步这一项合成操作；电脑可用时核对 PC 和网页云端内容。目标更新已发布，不重跑同一个 staging 的发布命令。若用户暂停安装，保留 0.1.6、下载缓存和本地操作，等待后续处理，不把系统管控当作网络或签名错误。

驱动每次重新观察真实界面，遇到锁屏、登录页或外部系统页面就停止。可重用的 `android-offline-upgrade-baseline.py` 只操作已打开的合成待办，自动恢复原网络开关；已成功运行的基线不要再次执行。真机验收通过后再制作最终标签和 GitHub Release；GitHub 仅上传 Windows EXE、签名与哈希，不上传 APK、私密测试数据或密钥。
