# 计划舱云端发布交接

2026 年 10 月 12 日，0.1.4 新版已独立部署并完成数据库及 PC 自动验收。现有公网域名仍使用旧服务，尚未正式切换；正式签名安装包和公开更新清单没有发布。本次保留了旧容器、旧镜像、旧数据卷、原安装客户端和本地离线队列。

## 已完成与版本

Web、Spring Boot、Tauri Windows 与 Android 版本统一为 `0.1.4`，Android `versionCode=8`。`node scripts/verify-release.mjs` 校验八处元数据。后端打包统一为 `plan-platform-api.jar`，不再让各脚本依赖旧版号文件名。

官网包含三种工作方式介绍、网页版入口、Windows 下载入口及 Android 二维码面板。下载入口由真实发布清单控制：当前 Windows 和 Android 条目均为空，页面明确显示正式安装包尚未发布，不提供测试包冒充正式版。

新客户端首次启动默认连接 `https://plan.zijun701.cn`；已有保存的服务器地址保持原样，不会自动替换本机测试配置。公网切换完成后，旧测试客户端须在服务器设置中主动切换地址并重新登录。离线队列按服务器和账号隔离，不会自动把本地测试账号或未同步队列迁入生产账号。

## 实测结果

| 验收范围 | 实际结果 |
| --- | --- |
| Spring Boot 集成测试 | 29 项通过，含邮箱绑定、单次使用且不开放注册的管理员初始化 |
| Vue 单元测试 | 129 项通过 |
| 完整浏览器验收 | 62 项通过，6 项按原设计跳过 |
| Windows 独立原生 EXE | 12 项通过，含今日待办、休息日、路线阶段、离线编辑重开、同步冲突及账号隔离 |
| Rust 单元测试 | 9 项通过；依赖托管 HTTPS 更新实验环境的 1 项未运行 |
| 原生回归工具自身测试 | 6 项通过 |
| Android Java 单元测试 | 11 项通过；独立测试 APK 构建及签名检查通过 |
| Android 0.1.4 真机 | 待用户连接 USB 后验收，未计入通过项 |
| MySQL 8.4 | V1 至 V13 全部成功；实际核心 CRUD、用户隔离、409 和幂等验收通过 |
| MySQL 恢复演练 | 生产空库及含合成业务数据的验收库均恢复至独立新库，逐表校验内容一致 |
| 新云端官网 | 经 SSH 本机转发，电脑和手机视口、三模式预览、登录连接、备案链接及下载禁用检查通过 |

本地证据位于被 Git 忽略的 `acceptance-results/`：`native/latest.html`、`browser-release-014.log`、`desktop-unit-014.log`、`cloud/desktop-home.png` 与 `cloud/mobile-home.png`。原生测试使用独立应用身份，没有安装、卸载或清理正式客户端。本轮没有重新实测正式安装包应用内升级，因此不把此前 0.1.3 的人工升级证据当作 0.1.4 的正式发布验收。

## 服务器独立部署

当前实例通过现有 SSH 密钥连接，账号为 `oyts`。新部署根目录 `/home/oyts/plan-platform`，首次候选目录 `release-0.1.4`；后续脚本目录为 `releases/版本-提交`，`current` 指向独立服务当前版本。

- 新 Compose 项目 `plan-platform`，独立卷 `plan-platform_mysql-data`；Web 仅绑定 `127.0.0.1:43140`，MySQL 不发布端口。
- 旧项目 `/opt/plan-console`、旧入口 `127.0.0.1:43127`、旧容器及旧备份均保留。
- 新配置 `.env` 权限为 600，独立随机数据库密码与 JWT 密钥；管理员实际邮箱不进入仓库。
- 首次注册只允许受限授权链接中的邮箱和 token，七天过期。管理员密码由用户自己设置，当前未代建正式账号。SMTP 未配置，邮件验证和找回密码保持关闭；注册始终关闭，正常成员使用邀请。
- MySQL、API、Web 内存上限分别 640、640、64 MiB；实际验收后约 201、285、3 MiB。另有系统和旧服务开销，2 GB 主机不适合同时跑编译、MQ 或大数据服务。

官方基础镜像为 MySQL 8.4、Temurin 21 JRE Alpine、Nginx 1.27 Alpine。实例直拉 Docker Hub 曾超时，本轮通过经过官方校验和检查的 crane 工具下载 linux/amd64 官方镜像再导入，没有关闭 TLS 检查或使用未知公共镜像。

MySQL 原迁移 V7/V9 使用了其不支持的 `ADD COLUMN IF NOT EXISTS`。已将原 H2 文件原样保留至 `db/vendor/h2`，MySQL 使用 `db/vendor/mysql`，Flyway 同时扫描公共目录与 `{vendor}`。只修复了新建且无业务账号的新库的失败历史行，未修改旧服务数据库；已有正式 MySQL 实例升级前仍需独立检查历史与备份，不能自动删除 Flyway 记录。

## 先完成管理员自己的操作

服务器 `sudo` 仍要求密码，本轮不能修改宿主机 Nginx。不要向聊天发送该密码，也不要授权无限制免密 sudo。以下操作由用户在 FinalShell 执行。

1. 先用 SSH 转发预览新版，不改变公网入口。在 Windows 新开 PowerShell：

   ```powershell
   ssh -N -L 43141:127.0.0.1:43140 oyts@115.29.176.141
   ```

   浏览器访问 `http://127.0.0.1:43141/welcome`；这是 SSH 保护的预览，不是公网发布。

2. 在 FinalShell 查看受限的初始链接，勿复制进聊天或提交到 Git：

   ```sh
   cd /home/oyts/plan-platform/release-0.1.4
   cat admin-registration.url
   ```

   链接只能用于指定邮箱，用户自选密码。可在 SSH 预览地址将域名部分替换为 `http://127.0.0.1:43141` 先验证注册；生产 Refresh Cookie 为 Secure，完整持久登录须在正式 HTTPS 入口验证。初始文件仍放在首次候选目录，不会在升级时公开复制。

3. 确认新版和管理员注册后，在服务器执行入口切换：

   ```sh
   cd /home/oyts/plan-platform/current
   sudo sh scripts/switch-public-entry.sh
   ```

   脚本要求旧代理确实为 43127，先保存旧配置，验证健康、关闭注册，再切换至 43140。Nginx 校验或公网健康失败时恢复旧入口；不删除新数据库。通过后在 `https://plan.zijun701.cn` 验证登录、三模式、刷新保持登录、同步和导出。管理员会话确认后，在受限 `.env` 中清空 `BOOTSTRAP_ADMIN_EMAIL`、`BOOTSTRAP_ADMIN_TOKEN_HASH`、`BOOTSTRAP_ADMIN_EXPIRES_AT`，并执行 `docker compose -p plan-platform -f docker-compose.production.yml up -d backend`。

4. 如需恢复旧入口：

   ```sh
   cd /home/oyts/plan-platform/current
   sudo sh scripts/rollback-public-entry.sh
   ```

   回滚只恢复宿主机 Nginx 路由，不覆盖或清空新 MySQL。旧容器仍保持运行。

## 后续一键部署

在已经初始化的新平台上，先提交通过测试的代码，再双击 `deploy-cloud-release.bat`。它调用 `prepare-cloud-release.bat` 重新校验版本、运行后端和前端测试、构建并打包；只打包列举的 JAR、静态文件和部署脚本，不打包仓库、凭据、账号、日志或数据库。

上传时校验 SHA256；在独立版本目录复制权限 600 的服务器配置，先备份 MySQL，再构建预编译镜像并升级新 Compose 项目。最后验证健康和版本，更新 `current`。它不会自动切换旧公网入口，也不会卸载客户端。缺少配置、工作区未提交、重复版本目录或镜像不可用时安全停止；基础镜像必须事先可用。本次首次初始化通过单独部署完成，不应在一个尚无 MySQL 的空目录直接运行升级脚本。

Android CI 显式仅安装 `platform-tools`，避免 setup-android 默认列表里的已移除 `tools` 包在真正编译前失败。输入配置依据 [setup-android 的 action 定义](https://github.com/android-actions/setup-android/blob/v3/action.yml)。

## 备份和真实旧数据

用户级 cron 每日 03:15 执行 `/home/oyts/plan-platform/daily-mysql-backup.sh`，新 MySQL 的逻辑备份及 SHA256 侧文件上传到现有私有 OSS `plan-console-backups/mysql/`。首次实际备份和上传已通过，使用 ECS RAM Role，不保存长期 AccessKey。旧版 03:00 定时备份不受影响。当前不自动删除历史 MySQL/OSS 备份，需后续按容量设计保留策略。

恢复演练只创建 `plan_restore_时间` 独立库，不覆盖在线库。执行时暂停业务写入以便逐表比较同一状态：

```sh
cd /home/oyts/plan-platform/current
sh scripts/mysql-backup.sh
sh scripts/mysql-restore-drill.sh backups/实际文件.sql.gz
sh scripts/mysql-oss-upload.sh backups/实际文件.sql.gz
```

旧 Node 数据卷的本轮文件计数为 0，已另存快照于部署根目录的 `backups/old-node-pre-cutover-20261012.tar.gz`。这不证明用户旧客户端数据为空或已迁移：纯前端数据可能仍在浏览器或手机本地。需要用户导出真实旧 JSON，然后按现有 [旧版迁移流程](legacy-migration.md) 预检、快照、导入与核对；不要用测试账号替代正式账号，也不要直接复用 Node tar 作为 MySQL 备份。

## 客户端正式发布和早晨手机验收

当前只有测试更新密钥及 Android debug keystore；没有发现正式长期签名身份。本轮不能签出可信正式升级包、启用公开下载或强制更新。HTTPS 证书、Tauri 更新签名、Windows 发布者签名和 Android 应用签名各自独立；换 Android 签名不能同应用直接覆盖升级，须先明确正式身份和已有测试数据的迁移方式。

保持原应用不动，连接并授权手机后运行：

```powershell
# 若后端 JAR 或客户端源码自构建后改变，先重建独立包，避免校验拒绝旧产物。
powershell -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action build
powershell -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action run -Platform android
```

独立 APK 为 `.desktop-tools/native-regression/PlanCabin-0.1.4-debug.apk`，包名 `cn.zijun701.plan.mobile.regression`，不覆盖正式计划舱。需要用户解锁并允许测试包安装。PC 本轮报告在 `acceptance-results/native/latest.html`；手机缺席不能记为测试通过。

待签名身份确定、正式两端包验收且云端 HTTPS 切换完成后，再按 [更新发布流程](client-updates.md) 发布 `/client-updates/` 与 `/downloads/manifest.json`。保留可选更新、必更策略和默认关闭的自动下载；不能靠改错误提示、关闭签名或 TLS 校验解决连接问题。
