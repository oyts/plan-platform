# Windows 正式更新源准备

Windows 0.1.4 的正式官网下载和更新源已于 2026 年 10 月 13 日经维护者批准启用。使用之前已安装验收的长期签名 EXE，不重新构建、重签或替换原候选包；Android 官网下载和公网更新源继续关闭，公开注册保持关闭。Android 正式 APK 仅供维护者向受邀测试成员私下提供，见 [受邀客户端测试](invited-client-testing.md)。

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

每次新的公开发布都需维护者明确确认。Windows 静态发布范围只限安装文件及官网下载状态，不发布 APK、不开放注册，不包含后端构建或数据迁移。先核对实际 Nginx 配置与静态目录、保留原配置及下载清单，再上传不可变安装包，最后启用两个清单。

正式启用后，必须通过外网 HTTPS 核对安装包哈希和真实签名、清单缓存头、官网下载、Android 下载关闭、API 健康与注册关闭，再在已安装的正式 Windows 应用执行更新检查。任一步失败，恢复上一份静态入口配置与清单；保留已上传版本文件用于排查，不删除业务数据或用旧 EXE 强制降级。

## 正式发布结果

官网下载：[计划舱官网](https://plan.zijun701.cn/)。Windows 更新源为 `https://plan.zijun701.cn/client-updates/windows/latest.json`，发布版本 0.1.4，可选更新。Android 更新清单和 APK 地址仍返回 404，官网不展示 APK 下载二维码。

真实公网 HTTPS 验收 5 项通过：后端健康与注册关闭、更新和下载清单 `no-store`、两条 EXE 地址的大小与 SHA256 及真实签名、不可变版本文件缓存、桌面和手机网页视口的真实下载与登录入口。发布后完整网页业务回归 27 项通过，包含三模式、离线编辑重放、409 冲突和用户隔离。凌晨回归发现服务器容器的 UTC 日期与客户端默认日期不一致；除了让脚本明确选择合成记录日期，还在数据库备份后修正后端运行时区为 Asia/Shanghai，加入与客户端北京时间一致的断言。

已安装的正式 Windows 0.1.4 客户端实际点击更新检查后显示“当前已是最新版本”，原生连接测试显示已识别 Spring Boot 多用户服务；自动下载未勾选，账号仍保持登录。这是正式更新源检查，不是跨版本安装验收。本轮没有连接 Android USB 设备；APK 重新通过 apksigner、包名、版本和 SHA256 核验，三端交叉读写沿用此前同一 0.1.4 业务版本的 9 项真实云端联测证据。

Windows 静态发布仅重新创建网页容器以挂载只读目录。首次静态启用因目录缺少 Nginx 工作用户遍历权限返回 404，已自动回滚，再修正公开目录权限并复验成功。部署元数据维持 600 权限，公开文件为 644，公开目录为 755。

随后修正后端运行时区，先通过现有脚本生成并验证 MySQL 备份，再只重建后端容器，不构建新镜像或执行数据库迁移；MySQL 与旧部署保留。`/home/oyts/plan-platform/production-timezone.yml` 保存无凭据的运行覆盖，生产 Compose 源码也加入相同 TZ 与 JVM 参数。后端健康后重新加载容器 Nginx，保持 HTTPS 及注册策略不变。

持久化静态目录为 `/home/oyts/plan-platform/client-public`，当前指针为 `releases/windows-014-yJuIcE`。`compose.yml` 只添加网页服务的两个只读挂载，不含密码。未来运行 `scripts/deploy-cloud-release.ps1` 会保留该覆盖文件；手工重建网页时也必须增加 `-f /home/oyts/plan-platform/client-public/compose.yml`。若改变后端代理规则，须同步审核静态覆盖 Nginx 配置，不应复用不兼容配置。

本次回滚命令在 ECS 的 SSH 终端执行：

```sh
sh /home/oyts/plan-platform/client-public/releases/windows-014-yJuIcE/publish.sh rollback windows-014-yJuIcE
```

回滚只恢复网页挂载与下载入口，保留已上传版本和业务数据库，不强制降级客户端。报告位于 Git 忽略的 `acceptance-results/windows-public-cloud/` 和 `acceptance-results/public-web/`；本地候选元数据保留发布前状态，不能用它代替实际公网验收。
