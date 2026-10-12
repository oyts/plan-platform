# 计划舱 Plan Platform

一个支持周期计划、目标路线、今日待办和任务计时的开源多用户平台。项目由原本的纯前端“计划控制台”重构而来，面向 Java 学习、个人部署和跨设备任务管理。

当前代码版本统一为 `0.1.4`：后端、Web、Windows 和 Android；Android 构建号 `8`。后端与 Web 已通过正式 HTTPS 入口启用；Windows 候选包已保留原数据覆盖安装，Android 正式新包已并存安装，两端正式包均通过云端三模式加载、连接检测与重开保持登录验收；独立客户端的真实云端联测 9 项通过。加密签名备份、隔离恢复及新云端副本下载核验已完成，Windows 正式发布目录已在本机准备并测试，尚未公开上传或启用，见 [Windows 更新源准备](docs/windows-public-release.md)。Android 下载继续等待 APP 备案。此前人工验收的本地原生更新版本为 `0.1.3`。

成员邀请页新增手机卡片与统一的底部编辑面板，桌面保留表格；手机布局专项测试及新版原生云端登录重跑通过，详见 [成员邀请手机验收](docs/mobile-members.md)。公网 Web 资源尚未因此更新。

## 当前状态

> **产品官网与今日待办阶段（2026-10-12）**
>
> 已完成多用户业务、旧版迁移、邀请注册、邮件验证、PWA 安装和移动端适配。普通业务写入现已支持 IndexedDB 离线队列、服务端幂等重放与 409 冲突停靠；计时类操作仍严格要求在线。
>
> 新增 Tauri 2 Windows 客户端、本地应用壳、可配置服务器、Windows 凭据管理器会话和签名更新基础。0.1.3 双端本地应用内更新已人工验收；0.1.4 新版云端已经启用，公开客户端更新源尚未发布。
>
> 已分离周期计划、目标路线和今日待办的工作区。新增公开产品首页、网页版入口、受发布清单控制的 Windows 下载与 Android 二维码，以及独立待办的日期、完成状态、编辑面板、分页、回收站和离线队列。当前阶段详情见 [官网与待办](docs/product-website-and-todos.md)。上一阶段 Windows 独立 EXE 与 Android 真机的 11 项回归记录见 [路线阶段](docs/route-stages.md)；本阶段不以浏览器手机视口测试代替 APK 真机验收。
>
> 阿里云新版已在独立 Compose 项目、MySQL 卷与本机 43140 端口部署，正式域名已切换到 Spring Boot 0.1.4。管理员由用户自行注册，权限已持久保存，一次性初始化授权已关闭；用户确认刷新后保持登录。MySQL 8.4 全部 13 个迁移、核心业务隔离与冲突、备份恢复及 OSS 上传通过；0.1.4 独立 Windows EXE 的 12 项回归通过；Android 独立 APK 构建 8 的 12 项真机回归及手机原生 HTTPS 云端连接检查通过。长期签名候选包已在双端安装，正式用户会话与三模式只读验收通过，公开下载仍未开启。详见 [云端发布交接](docs/cloud-release-014.md)。旧服务及备份继续保留。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Spring Security、OAuth2 Resource Server、Spring Data JPA、Flyway。
- 数据库：开发环境使用 H2 文件数据库；部署环境使用 MySQL 8.4。
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Vite 6。
- 工程化：Docker Compose、Nginx、GitHub Actions、Maven 集成测试。

项目当前使用 Spring Boot 3.5 系列；框架大版本升级应在独立分支验证接口、数据库迁移与客户端兼容性。

## 已实现

- 邮箱注册开关、登录限流、15 分钟 JWT Access Token、HttpOnly Refresh Cookie、令牌轮换与多设备会话管理。
- 个人资料维护、当前密码验证的密码修改、SMTP 驱动的一次性找回密码链接；密码变更或找回会撤销全部刷新会话。
- 用户级数据隔离，任何计划、标签、任务和主线查询都包含所有权条件。
- 计划、标签、任务的增删改查与输入校验。
- 独立今日待办：按日期查看、快速新增、完成勾选、侧边编辑、搜索与每页 10 项展示、软删除与恢复；不混入周期计划或目标路线统计。
- 公开产品首页、三种模式展示、网页版入口、真实发布清单驱动的下载按钮和本地生成的 APK 二维码。
- 任务开始、暂停、完成、手工补录、验收结果、结果备注与计时流水。
- 今日控制台、日期范围统计、每日复盘。
- 目标路线与阶段 CRUD；自由推进、顺序推进及指定前置依赖由后端校验，拒绝循环和越权依赖。
- JPA `@Version` 乐观锁，多设备使用旧版本提交时返回 HTTP 409。
- Flyway 数据库版本管理；公共迁移加 H2/MySQL 厂商迁移目录，保留已运行 H2 脚本的校验和。
- 旧版 1.x JSON 预检、原始快照、关系型导入、幂等控制、迁移报告与批次回滚。
- Docker Compose 一键启动 MySQL、Spring Boot、Vue/Nginx。
- PWA 安装、应用壳离线缓存、版本更新提示与移动端响应式布局；Service Worker 不缓存私有 API，已访问的业务数据使用账号与服务器隔离的 IndexedDB 快照。
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

正式 HTTPS 网页可双击 `verify-public-web.bat`，使用 Python + Playwright 和两个受邀请的普通验收账号自动操作三种模式。2026-10-12 桌面与手机网页视口全量 27 项通过、0 失败；包含缓存离线编辑、顺序同步、真实 409 冲突及用户隔离。准备步骤、安全边界和复跑命令见 [正式网站自动验收](docs/public-web-acceptance.md)。不把手机视口结果当作 APK 真机验收。

正式云端的 Windows、Android 真机与网页联测可双击 `verify-cloud-native.bat`。2026-10-12 全量 9 项通过、0 失败，包含原生 HTTPS 登录、三模式数据互通、真实客户端重开、冲突拒绝及账号隔离；不使用本地 API，不覆盖正式应用。准备步骤见 [云端账号原生联测](docs/cloud-native-acceptance.md)。

手机布局使用底部导航、独立任务编辑面板和日程详情面板，任务列表支持搜索、筛选及每页 10 项展示。设计边界、横竖屏检查和本轮验收记录见 [手机布局优化](docs/mobile-layout.md)。

顶栏可以切换“周期计划”“目标路线”和“今日待办”，各自记忆浏览位置。目标路线不要求按日历推进，新建默认自由推进，也可配置前置步骤；阶段支持折叠及每组 10 项展示。工作区设计见 [工作区模式](docs/workspace-modes.md)，路线规则见 [路线阶段](docs/route-stages.md)，官网与待办见 [官网与待办](docs/product-website-and-todos.md)。

## Docker 部署

复制配置并填写强密码：

```bash
copy .env.example .env
docker compose up -d --build
```

访问：http://localhost:43127

本地 Docker 首次体验可临时设置 `REGISTRATION_ENABLED=true`，创建账号后再关闭。生产环境必须修改 `JWT_SECRET`、数据库密码和允许跨域的域名，并设置 `REFRESH_COOKIE_SECURE=true`；阿里云部署时建议使用 HTTPS、独立数据库备份、日志采集和安全组最小开放原则。

部署前、上线和回滚检查见 [部署清单](docs/deployment-checklist.md)。接口契约见 [OpenAPI 文档](docs/openapi.yaml)。

生产环境使用 `docker-compose.production.yml`，不是上面的本地体验配置。`prepare-cloud-release.bat` 构建并验证不含凭据的部署包；`deploy-cloud-release.bat` 在已初始化的新平台上备份、上传、独立升级，不自动切换旧公网入口。初始化、端口与回滚步骤见 [云端发布交接](docs/cloud-release-014.md)。

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
│        ├─ todo/        独立今日待办
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

仓库：[oyts/plan-platform](https://github.com/oyts/plan-platform)。首次获取代码：

```bash
git clone https://github.com/oyts/plan-platform.git
cd plan-platform
```

IDEA 和 VS Code 都打开同一个仓库目录，不需要各建一个 Git 仓库。操作步骤及项目经历边界见 [GitHub 与 IDE](docs/github-and-ide.md)。贡献指南见 [CONTRIBUTING](CONTRIBUTING.md)，安全报告见 [SECURITY](SECURITY.md)。项目使用 [MIT License](LICENSE)。
