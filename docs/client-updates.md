# 客户端更新与本地验收

0.1.1 为 Windows EXE 与 Android APK 加入更新提示、下载和安装入口。当前只完成本地实现与测试包准备，不改动阿里云线上服务、不自动安装。界面沿用现有白蓝配色，在服务器设置中展示安装版本、发布说明和明确的操作状态。

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

本轮前端 78 项、Android Java 10 项、Rust 原生 7 项和浏览器 28 项测试通过，其中浏览器包含桌面/手机尺寸的更新桥接模拟。Web、EXE、APK 构建通过，APK 签名验证通过；发布目录生成器已验证实际包的版本与签名。浏览器模拟并不证明操作系统原生安装成功。尚未覆盖安装这一轮更新功能。

下一步先让当前 0.1.0 覆盖安装到 0.1.1，这一次是必要的手动引导；然后用更高版本的同签名测试包和独立测试发布源验证：

1. 普通提示可延后，自动下载默认关闭、主动勾选后保持，下载后不会自行安装。
2. 取消安装后仍能使用原版本；更新成功后账号、计划、休息日与待同步队列保持。
3. 必要升级阻止编辑，但同步队列和导出入口仍可访问。
4. 坏签名、错哈希、错包名、旧构建号、下载中断、系统拒绝安装均不能覆盖现有应用。

验收完成后才确定正式签名并发布云端更新源。当前不能因为公网尚无 `latest.json` 而把检查错误当作业务服务器故障，也不要为此关闭 HTTPS 或签名检查。
