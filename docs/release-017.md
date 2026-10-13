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
| PlanCabin-0.1.7-release.apk | `cb9dffd6db17aca002b2ba61ccb4c3f87f799b7ad8a6d3b6d973797aba281552` | 原签名、包名与受限更新 URL 已核验；尚未执行应用内安装 |

Android 构建号 12 仅准备在私有 staging 中，当前受限清单仍指向已验收接入包 0.1.6 / 11；未将目标 APK 手动覆盖到手机。

## Android 剩余验收

OEM 输入法会重排 ADB 键盘事件，已停止使用该输入方式保存草稿。一次输入探测产生的合成待办 `0261013－1420NU` 留在测试现场，真实待办未编辑。后续改用独立测试 APK 的原生无障碍精确文本输入，限制在正式应用前台的合成待办标题和搜索字段；不读取会话、不操作密码、不启用正式应用调试。

辅助包编译及 Python 输入保护的 6 项单元测试通过，但手机系统多次返回 `INSTALL_FAILED_ABORTED: User rejected permissions`，尚未安装成功。等待用户允许系统安装后，继续创建非空离线队列，再从应用内下载并安装 0.1.7，检查会话、数据、队列保留及同步。此阻碍不影响已上线的网页和 Windows 使用。

Windows 发布后，受限 Android 接口再次通过 4 项真实 HTTPS 验证，两个普通验收账号下载的 0.1.6 / 11 APK 哈希匹配，未登录下载拒绝且公开 APK 不可访问。报告为 `acceptance-results/invited-android/2026-10-13T06-57-06-642Z-ad25fca4/report.json`。

## 续做命令

先完成手机系统安装授权，不绕过拦截或关闭安全保护。辅助测试 APK 为 `%TEMP%/plan-cabin-android-build/app/outputs/apk/androidTest/debug/app-debug-androidTest.apk`；其目标包是独立回归包，不是正式应用。授权安装后，前台返回正式计划舱并清除输入探测的搜索条件：

```powershell
& .desktop-tools/public-web/venv/Scripts/python.exe scripts/android-ui-acceptance.py --serial 10AF7328GX0052H search
```

该驱动每次重新观察真实界面，仅允许已知导航和 `nativeupgrade-` 前缀合成内容，输入不精确或遇到锁屏、登录页就停止。创建并验证非空离线队列后，才启用已准备的目标更新清单：

```powershell
powershell -File scripts/publish-invited-android.ps1 -StageDirectory production-artifacts/invited-android/0.1.7/candidate-QL143e
```

随后从手机应用内完成下载和安装，不用 `adb install` 推送目标正式 APK。真机验收通过后再制作最终标签和 GitHub Release；GitHub 仅上传 Windows EXE、签名与哈希，不上传 APK、私密测试数据或密钥。
