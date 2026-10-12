# Windows 正式更新源准备

Windows 0.1.4 的正式下载和更新文件已在本机准备并验收，尚未上传或启用。使用之前已安装验收的长期签名 EXE，不重新构建、重签或替换原候选包；Android 下载继续等待 APP 备案，公开注册保持关闭。

## 发布范围

官网下载清单只提供 Windows，`android` 为 `null`。Windows 更新清单的 `minimumSupportedVersion` 固定为 `0.0.0`，不启用必要升级，也不改变用户默认关闭的自动下载偏好。当前已经安装 0.1.4 的正式客户端，在正式源启用后应显示当前版本最新；这不等于完成了跨版本应用内安装验收。

旧测试签名的 0.1.3 不能自动信任新长期签名。仍使用旧测试身份的客户端需要保存数据后手动迁移，不通过降低签名或 TLS 校验来升级。

生成目录中只有以下五份公开文件；发布元数据另存，不应一同对外提供：

| 路径 | 用途 |
| --- | --- |
| `public/client-updates/windows/latest.json` | Windows 更新检查，可选更新 |
| `public/client-updates/windows/releases/0.1.4/Plan Cabin_0.1.4_x64-setup.exe` | 更新安装包 |
| 同目录的 `.exe.sig` | 真实 Minisign 更新签名 |
| `public/downloads/manifest.json` | 官网 Windows 下载清单，Android 关闭 |
| `public/downloads/0.1.4/Plan Cabin_0.1.4_x64-setup.exe` | 官网直接下载，与更新安装包字节相同 |

两个 JSON 清单需要 `Cache-Control: no-store`；版本目录里的文件发布后不可原地替换，可设置长期不可变缓存。URL 只使用现有 HTTPS 域名，不包含账号、口令或访问令牌。Windows 未配置受信任的 Authenticode 发布者证书，更新签名验证不能替代它。

## 本地准备命令

在本机 Windows PowerShell 的项目目录执行。三个路径要替换为实际已验收的候选目录、新签名备份目录和网盘下载副本；不要传入口令 TXT。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-windows-public-release.ps1 `
  -CandidateDirectory '已验收的production-artifacts候选目录' `
  -SigningBackupDirectory '新签名备份的受限目录' `
  -CloudBackupCopy '从网盘下载的.plan-signing-backup完整路径' `
  -ConfirmWindowsAcceptance -ConfirmOldCloudPairCleanup
```

仅在正式 Windows 安装验收通过、维护者确认清理旧云端备份与同处存放的口令后，使用上述确认参数。脚本核对四端版本一致、候选包大小和哈希、真实长期签名与可信版本注释、新备份恢复证明、口令独立保管及下载副本哈希。全部核对通过才创建一个新目录，不覆盖原产物。

准备输出在 `production-artifacts/windows-public-release/0.1.4/` 下的独立目录。`metadata/release-plan.json` 保持 `PREPARED_NOT_PUBLISHED` 与 `publicReleaseApproved: false`。脚本不安装应用、不上传文件、不改 Nginx、数据库或注册开关；原签名密钥、备份、APK 和账号数据不会复制到公开目录。

## 本机验收结果

2026 年 10 月 13 日，真实候选 EXE 的大小为 3,455,546 字节，SHA256 为 `26b50acb9b7d3ced2e083cd85e51997bc82231366b1a979189b343c632ae373f`，长期 Minisign 载荷与可信注释验证通过。

发布、签名、备份和更新策略 22 项测试，前端 131 项测试、类型检查与生产构建通过。本地浏览器与静态文件验收 4 项通过：可选 Windows 清单、非公开文件拒绝访问、桌面与手机视口的真实 EXE 下载和哈希，以及 Android 不显示下载二维码。登录页使用明确的关闭注册夹具，不登录真实账号；这不是公网权限验收。

复跑时先构建 Web，再传入本地发布目录：

```powershell
$env:PLAYWRIGHT_BROWSERS_PATH = '.desktop-tools/playwright'
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/windows-public-release-smoke.mjs '本地发布目录'
```

脚本仅启动本机随机端口的临时 HTTP 服务，结束后关闭；它没有让正式客户端绕过 HTTPS。报告及公开页面截图在 Git 忽略的 `acceptance-results/windows-public/`。EXE 下载后只比较字节和哈希，不执行安装。

## 正式启用前与回滚要求

正式上传与启用仍需维护者明确确认，范围只限 Windows 静态文件及官网下载状态，不发布 APK、不开放注册、不部署后端或迁移数据。先核对实际 Nginx 配置与静态目录、保留原配置及下载清单，再上传不可变安装包，最后启用两个清单。

正式启用后，必须通过外网 HTTPS 核对安装包哈希和真实签名、清单缓存头、官网下载、Android 下载关闭、API 健康与注册关闭，再在已安装的正式 Windows 应用执行更新检查。任一步失败，恢复上一份静态入口配置与清单；保留已上传版本文件用于排查，不删除业务数据或用旧 EXE 强制降级。

本阶段没有修改阿里云服务。公网两个更新清单仍未启用，原先的 404 提示要等正式启用并复验后才能记为解决。
