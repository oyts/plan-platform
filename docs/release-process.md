# 版本发布流程

发布是“源码校验 → 构建 → 实际安装验收 → 云端升级 → 下载发布 → GitHub 归档”，各步结果单独记录。不能把编译成功写成真机通过，也不能在尚未验收时勾选发布工具的确认开关。

## 版本与测试

1. 同步 Web package/lock、Tauri/Cargo、Android、后端 pom/application 的版本；Android 构建号必须递增。运行 `node scripts/verify-release.mjs`。
2. 日常小版本只测试新增业务和修改直接影响的旧功能，不运行无关业务回归；大版本更新才进行全面测试。每版先更新 `scripts/release-test-scope.json` 的版本、影响说明和测试文件。构建、签名/哈希、版本一致、升级数据保留和云端健康是发布门槛，不等于重跑所有业务。测试 API 使用独立数据库，真实云端测试只用受邀合成账号，不写管理员真实计划。
3. 使用 `scripts/prepare-formal-clients.ps1` 构建候选包，核对原 Windows 公钥、Android 包名与证书。签名私钥和口令不进入 Git、聊天、日志或发布目录。

## 双端升级

用户先保存并关闭正式 PC 客户端。`scripts/install-formal-windows.ps1` 在原目录备份应用数据，再以 `/UPDATE` 覆盖，启动前逐文件比对。Android 使用同包名、同证书的 `adb install -r`，不得卸载或清数据。

每次升级核对原账号、已有数据和队列保留、重开及版本；具体业务只验收本次影响范围，不对未修改的三模式功能重新做完整增删改查。覆盖安装证据与应用内下载更新证据分开记录；队列为空时不得声称已证明非空队列升级保留。正式账号需要重新登录时交由用户输入密码。

云端和正式 Android 构建默认 `-TestScope affected`，按当版清单选择测试；大版本传 `-TestScope full`。原生验收脚本也必须选择当版相关用例，不因它包含旧用例就整套运行。0.1.9 仅运行导航单元、桌面/手机网页导航和正式双端导航/教学入口检查；不重测未变化的账号隔离、三模式业务写入、离线冲突和数据库恢复。每次发布仍备份真实数据库，不能以减少测试为由跳过备份。

## 云端升级和回滚

先确认当前健康、版本及账号配置；使用 `deploy-cloud-release.bat` 升级已初始化的新平台。脚本要求干净 Git，备份 MySQL，保留旧镜像、卷与发布目录，并继承 `client-public/compose.yml` 和 `production-timezone.yml`。不能再次初始化账号或抢占旧服务 43127。

外网检查 `/api/health`、`/api/auth/config`、官网与核心业务；失败时保留新数据，按该次部署的回滚记录恢复已验证入口。不要直接覆盖或降级数据库。

## Windows 下载与更新源

实际 Windows 验收及签名备份验证通过后，使用 `scripts/prepare-windows-public-release.ps1` 准备 Windows-only 文件，再由 `scripts/prepare-client-static-deployment.mjs` 打包。远端核对上传 SHA-256，使用打包后的 `publish.sh activate <run>` 切换。源文件含版本占位符，不能直接拿未渲染的脚本执行。

发布时只切换当前清单；旧版本 EXE 和签名必须逐字节保留，拒绝同版本不同内容。`verify-public-windows-release.mjs` 核对真实 HTTPS 下载、签名、缓存策略、官网及 Android 404。更新维持可选，自动下载默认关闭；必更策略须单独决策，不因小修复自动强制。

Android 在完成所需备案和公开发布授权前仅受邀分发正式 APK：官网下载清单 `android=null`，公开 Android 更新清单与 APK URL 保持 404。新受邀客户端通过登录后的 API 获取更新，APK 只挂载到后端；发布、鉴权验证与回滚见 [受限 Android 更新](invited-android-updates.md)。

Android CI 可以构建、测签名和运行单元测试，但本阶段不上传可下载的 APK 附件。朋友私测只提供已验收的正式同证书 APK，不使用 CI 调试包替代。

## GitHub

检查变更与秘密泄漏风险，按明确路径提交并推送 `main`。验收完毕再创建 `v<版本>` 标签和 GitHub Release，记录测试范围、兼容边界和回滚方式。公开附件限 Windows EXE、其签名及校验和；不上传私测 APK、账号、数据库、测试录屏、真实日志或任何签名私钥。每次版本的真实结果与未完成项写入对应发布记录。

`node scripts/github-windows-release.mjs --check` 只核对现有 Git 登录和仓库权限，不弹出登录、不存令牌。确认云端与 Windows 发布完成后，在干净 Git 中创建并推送指向已验收 HEAD 的标签，再执行 `--publish <已验收的 Windows 发布目录>`。工具先建草稿、核验附件哈希，再公开 Release；失败时保留草稿，不替换已公开版本。凭据只在进程内存中用于 GitHub 自身，不附加给产品域名。GitHub 附件采用点号替代空格的传输文件名，原安装包字节和官网文件名不变。发布工具修复可以后补提交，但必须验证业务源码和版本元数据与原标签完全一致，不移动已有标签。
