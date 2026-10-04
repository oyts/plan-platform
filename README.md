# Plan Platform

一个支持周期计划、目标路线和任务计时的开源多用户平台。项目由原本的纯前端“计划控制台”重构而来，目标是形成可以学习、部署、开源并用于 Java 求职项目经历的完整工程。

当前版本：`0.1.0`（前后端分离基础版）

## 当前状态

> **目标路线阶段分组与灵活推进阶段（2026-10-04）**
>
> 已完成多用户业务、旧版迁移、邀请注册、邮件验证、PWA 安装和移动端适配。普通业务写入现已支持 IndexedDB 离线队列、服务端幂等重放与 409 冲突停靠；计时类操作仍严格要求在线。
>
> 新增 Tauri 2 Windows 客户端、本地应用壳、可配置服务器、Windows 凭据管理器会话和签名更新基础。0.1.3 双端本地应用内更新已人工验收，公开更新源与新版云端部署尚未启用。
>
> 已分离周期计划与目标路线的首页、导航、位置记忆、统计和回收站。目标路线新增阶段管理、折叠搜索分页、步骤编辑面板及自由/顺序/指定依赖推进，保留旧路线规则；普通步骤内容可离线编辑。后端 23 项、前端 108 项测试通过，完整浏览器 50 项通过、6 项按设计跳过。原生客户端本阶段结果见测试报告与阶段文档，不以浏览器结果替代真机结论；系统分享、后台行为和跨版本升级仍需专项验收。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Spring Security、OAuth2 Resource Server、Spring Data JPA、Flyway。
- 数据库：开发环境使用 H2 文件数据库；部署环境使用 MySQL 8.4。
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Vite 6。
- 工程化：Docker Compose、Nginx、GitHub Actions、Maven 集成测试。

选择 Spring Boot 3.5 而不是 4.x，是为了在保持官方稳定版本的同时，获得更成熟的求职项目生态和第三方兼容性。Spring Boot 3.5.16、4.0.7 和 4.1.0 均为官方当前稳定线；本项目后续可以单独建立升级分支验证 4.x。

## 已实现

- 邮箱注册开关、登录限流、15 分钟 JWT Access Token、HttpOnly Refresh Cookie、令牌轮换与多设备会话管理。
- 个人资料维护、当前密码验证的密码修改、SMTP 驱动的一次性找回密码链接；密码变更或找回会撤销全部刷新会话。
- 用户级数据隔离，任何计划、标签、任务和主线查询都包含所有权条件。
- 计划、标签、任务的增删改查与输入校验。
- 任务开始、暂停、完成、手工补录、验收结果、结果备注与计时流水。
- 今日控制台、日期范围统计、每日复盘。
- 目标路线与阶段 CRUD；自由推进、顺序推进及指定前置依赖由后端校验，拒绝循环和越权依赖。
- JPA `@Version` 乐观锁，多设备使用旧版本提交时返回 HTTP 409。
- Flyway 数据库版本管理；H2/MySQL 共用迁移脚本。
- 旧版 1.x JSON 预检、原始快照、关系型导入、幂等控制、迁移报告与批次回滚。
- Docker Compose 一键启动 MySQL、Spring Boot、Vue/Nginx。
- PWA 安装、应用壳离线缓存、版本更新提示与移动端响应式布局；私有 API 数据不做离线缓存。
- IndexedDB 离线操作队列、`X-Idempotency-Key` 服务端响应重放和可人工处理的多端冲突中心。
- Windows x64 EXE 安装包、原生安全请求、服务器配置、按服务器隔离的离线队列与签名更新基础。
- Android APK、Java 受限请求、Keystore 会话加密、系统分享与返回键适配。
- 持久化 FIFO 离线顺序、队首失败即暂停、支持 Web Locks 的跨窗口同步互斥。

## 本地运行

环境要求：Java 21、Maven 3.9+、Node.js 22+（原生 Android 构建工具需要）。

最简单的方式是在 Windows 双击：

```text
start-dev.bat
```

也可以分别启动：

```bash
cd backend
mvn spring-boot:run
```

```bash
cd web
npm install
npm run dev
```

- Web：http://localhost:43128
- API：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health
- H2 数据文件：`backend/data/plan-platform.mv.db`

## 测试

Windows 可直接双击 `verify.bat`，或执行：

```bash
cd backend && mvn test
cd ../web && npm run build
```

后端集成测试会真实启动 Spring 容器、执行 Flyway、注册两个账号，并验证计划创建与用户数据隔离。

跨会话业务验收可双击 `verify-cross-client.bat`。它在 43130/43131 端口启动独立服务，使用随机内存数据库，不读写开发库或生产库。验收范围、结果与手机准备步骤见 [跨客户端验收](docs/cross-client-acceptance.md)。

真实 PC 与 Android 手机业务回归：先双击 `prepare-native-regression.bat` 构建独立测试客户端，再连接并授权手机，运行 `verify-native-clients.bat`。结果在 `acceptance-results/native/latest.html`，不会覆盖现有客户端。范围、隔离规则和命令行用法见 [原生客户端自动化回归](docs/native-regression.md)。

手机布局使用底部导航、独立任务编辑面板和日程详情面板，任务列表支持搜索、筛选及每页 10 项展示。设计边界、横竖屏检查和本轮验收记录见 [手机布局优化](docs/mobile-layout.md)。

顶栏可以切换“周期计划”和“目标路线”，两边分别保留首页、导航、统计及回收站入口。目标路线不要求按日历推进，新建默认自由推进，也可配置前置步骤；阶段支持折叠及每组 10 项展示。工作区设计见 [工作区模式](docs/workspace-modes.md)，本阶段规则与离线边界见 [路线阶段](docs/route-stages.md)。

## Docker 部署

复制配置并填写强密码：

```bash
copy .env.example .env
docker compose up -d --build
```

访问：http://localhost:43127

本地 Docker 首次体验可临时设置 `REGISTRATION_ENABLED=true`，创建账号后再关闭。生产环境必须修改 `JWT_SECRET`、数据库密码和允许跨域的域名，并设置 `REFRESH_COOKIE_SECURE=true`；阿里云部署时建议使用 HTTPS、独立数据库备份、日志采集和安全组最小开放原则。

部署前、上线和回滚检查见 [部署清单](docs/deployment-checklist.md)。接口契约见 [OpenAPI 文档](docs/openapi.yaml)。

PWA 能力边界、手机回归步骤和自动检查见 [PWA 与跨端测试](docs/pwa-and-cross-client-testing.md)。离线协议、幂等键与冲突处理见 [离线同步说明](docs/offline-sync.md)。

Windows EXE 的安装、构建、验收与签名发布见 [桌面客户端说明](docs/windows-desktop.md)。双击 `build-desktop.bat` 构建安装包，`start-desktop.bat` 启动原生开发模式。
双击 `start-desktop-server.bat` 可启动仅供本机测试的独立 API（43129 端口与独立 H2 数据文件）。

Android APK 的安装、USB 本地测试和正式签名见 [Android 客户端说明](docs/android-client.md)。双击 `build-android.bat` 构建调试包；它与 Windows 客户端复用业务页面、账号和同步协议，正式签名发布与真机验收需另行完成。

## 目录

```text
plan-platform/
├─ backend/              Spring Boot API
│  └─ src/main/java/
│     └─ com.oyts.planplatform/
│        ├─ auth/        认证与令牌
│        ├─ plan/        计划、任务、进度、统计
│        ├─ mainline/    目标路线、阶段与依赖
│        ├─ migration/   旧版数据迁移
│        └─ common/      异常与基础实体
├─ web/                  Vue 3 Web 客户端
├─ docs/                 架构与学习文档
└─ docker-compose.yml
```

## 多客户端路线

所有客户端只负责界面与离线缓存，业务数据最终通过同一套 `/api` 接口进入服务器：

1. Vue Web：当前基准客户端。
2. Windows EXE：已使用 Tauri 2 封装 Vue，复用业务 API；macOS/Linux 安装包后续单独适配。
3. Android APK：已用 Capacitor 8 复用 Vue，Java 原生层负责受限 API 请求和 Keystore 会话加密；当前交付本地测试包。
4. 微信小程序：独立页面层，复用账号、计划、任务、主线和统计 API。

详细设计见 [架构说明](docs/architecture.md)、[账号与会话安全](docs/account-security.md) 和 [学习路线](docs/learning-guide.md)。

## 旧版迁移边界

旧版计划控制台的新导出文件会携带完整任务目录，可迁移自定义计划、暑假计划、吉他与项目升级主线及对应进度。较早的备份缺少内置任务定义，预检会明确提示只能保存相关原始快照。迁移验收完成前仍应保留原 `summer-plan-web` 和导出的 JSON 备份。详细流程见 [旧版数据迁移](docs/legacy-migration.md)。

## GitHub

测试完成后创建一个空 GitHub 仓库，再执行：

```bash
git remote add origin https://github.com/<你的用户名>/plan-platform.git
git push -u origin main --tags
```

项目使用 MIT License。
