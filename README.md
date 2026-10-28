# 计划舱 Plan Platform

一个支持周期计划、目标路线、今日待办和任务计时的开源多用户平台。项目由原本的纯前端“计划控制台”重构而来，面向 Java 学习、个人部署和跨设备任务管理。

当前正式 **Web/API、Windows官网／可选更新源、Android登录受限源均为0.1.25，Android构建30**，Flyway V34。阶段5三端定向验收与发布门槛已通过，包括双端真实应用内更新、合成非空队列保留、正式升级／冷重开及发布后检查；生产备份双恢复／OSS核验也已通过，见[0.1.25发布记录](docs/release-0125.md)。更新可选、自动下载默认关闭，官网APK／二维码与公开注册仍关闭；既有AI、邮件和成员治理配置保留。GitHub归档以[同名Release](https://github.com/oyts/plan-platform/releases/tag/v0.1.25)的实际状态为准。

后续开发统一看[0.1.20之后的核心功能规划](docs/core-function-roadmap-0120-plus.md)：阶段1—5已发布，阶段6正在进行核心综合回归和[UI重构交接](docs/ui-rebuild-handoff.md)，尚未冻结或开始视觉重构。往周复盘修复的0.1.26仅为候选，正式三端仍0.1.25；实际完成与待验收项见[检查点](docs/core-freeze-progress.md)。旧六阶段规划继续作为已交付历史保留。

核心阶段1—4已分别随0.1.21—0.1.24完成开发、定向验收和三端发布。[阶段4实际结果](docs/metrics-progress.md)包含Android独立三组、正式升级五组、受限源四组和原生更新两组；各轮不累加为新业务覆盖。已验收正式APK可供现有受邀成员使用，独立调试包不得替代正式包。[阶段5更新体验](docs/update-experience-progress.md)现已三端交付；测试身份的未来版本26不是正式发布，不能分发朋友。阶段6尚未开始。

历次升级证据分别保留在对应发布记录中，不累加成当前版本的测试数量。例如0.1.16的Windows验收曾由用户重新登录，不能作为跨升级会话保留证明；0.1.19的原会话与重开另有实际证据。默认AI每天20次、管理员热修改、公众注册关闭及官网APK／二维码关闭保持不变。

旧正式 Android 的临时匿名迁移窗口已于北京时间 **2026-10-15 02:17:33** 到期，旧匿名地址现已返回 404，不重新开放。已接入受限通道的客户端继续登录后更新；未迁移的旧版本需私下取得同包名、同签名的正式 APK。见 [迁移说明](docs/legacy-android-migration.md)。

[产品官网](https://plan.zijun701.cn/welcome)介绍三模式、AI工作台、提醒、复盘和个人模板库，包含草稿示例、确认后生成与已有规划调整、六节互动教学、跨端与离线边界。官网只介绍已发布能力；网页版入口和Windows下载可用，Android官网APK／二维码仍关闭。早期官网独立发布过程见[官网展示与独立发布](docs/product-website-and-todos.md)，当前交付以0.1.22记录为准。

首次使用可从“使用教学”练习六节小案例，包含整套模板；AI教学使用本地模拟，不扣调用次数。已有规划的节奏示例只填写要求，发送和应用仍由用户确认；既有教学进度保留。

`0.1.14` 的共享汇总不会自动修改任务；周复盘记录和提醒设置要求联网保存，已加载页面可离线只读。目标路线不标记逾期，旧待办未知完成时间不伪造回填。见 [功能边界](docs/daily-experience.md)。

日常小版本仅验证新增和受影响功能，大版本才执行全面回归；签名、版本、升级数据保留、生产备份与云端健康仍为发布门槛。见 [发布流程](docs/release-process.md) 与 [导航体验优化](docs/navigation-experience.md)。

六阶段范围及完成情况见[整合开发规划](docs/next-development-roadmap.md)，最终交付与已知边界见[交付索引](docs/stable-handoff.md)。邮件安全、[AI工作台与手机输入体验](docs/ai-workbench.md)、[系统提醒](docs/native-reminders.md)、[日周复盘和AI分析](docs/reflection-workspace.md)、个人模板库与试用收口已分别随0.1.16—0.1.19交付。上一轮六阶段目标保留在[历史规划](docs/development-roadmap.md)，已实现[反馈与脱敏诊断](docs/feedback-and-diagnostics.md)、[个人数据中心](docs/personal-data-center.md)、[编排与长期历史](docs/orchestration-and-history.md)、[离线范围及冲突体验](docs/offline-experience.md)、帮助／本地演示和[运行证据与成员治理](docs/operations-and-governance.md)。历史验收见[续做记录](docs/roadmap-progress.md)。清理仅预览，真实删除仍须另行确认。

0.1.4 已完成 HTTPS 新版部署、MySQL 备份恢复、加密签名备份、Windows 官网下载及更新源发布，见 [Windows 发布记录](docs/windows-public-release.md)。Android 继续只提供受邀测试 APK，官网下载等待 APP 备案，公开注册保持关闭，见 [受邀客户端测试](docs/invited-client-testing.md)。成员页手机卡片与底部编辑面板已进入共享源码，桌面保留表格，详见 [成员邀请手机验收](docs/mobile-members.md)。

第五阶段[个人整套模板库](docs/personal-template-library.md)与第六阶段模板教学已随三端0.1.19发布，支持个人结构快照、分类收藏、预览确认建立新副本和私有备份格式4。Android系统文件选择／只读预检及后续QQ本人收件哈希核对已完成，详见[历史收口记录](docs/historical-closure-progress.md)，不推断所有外部应用均已通过。

## 当前状态

历史问题收口已随0.1.20正式发布：旧迁移批次回收站事务补迁、双邮箱变更（经确认启用）和Windows系统另存为。手机完整交互、本人双邮箱真实收信、锁屏／点击／重启后未来提醒、外部分享接收及Windows导出字节都有独立证据，见[历史问题记录](docs/historical-closure-progress.md)。[当前源码契约](docs/openapi-current.json)覆盖202个MVC操作、声明DTO、审查后的动态正常响应及共同失败族；旧JSON标准字段指南明确保留宽容读取，所有权、事务和运行时边界见[接口说明](docs/core-api.md)，不宣称穷尽所有历史输入和网络错误。

0.1.19的六阶段交付及0.1.20的历史收口分开记录，不把旧测试累计为新版本数量。正式双端本轮验证原登录、空队列及冷重开；独立Windows非空队列、Android完整交互和真实系统文件操作有各自证据，不能互相替代。完整版本、归档状态、演示和维护交接见[交付索引](docs/stable-handoff.md)。

AI 支持官方 DeepSeek 异步对话、模式推荐与确认、草稿多次修订和手工编辑、三模式确认后原子生成、外部 AI JSON 导入，以及对明确选择的已有周期计划、目标路线和待办提出调整建议。修改前逐项展示差异，保护历史和所有权，冲突保留草稿，不删除后重建原计划。管理员每日额度热生效，调用幂等预占，未知结果不自动重复付费，手动业务不依赖 AI。独立 MySQL V14—V17、生产备份恢复和旧镜像回退通过；前两阶段 5 次、本轮 3 次官方合成验收均成功，未反复重跑付费脚本。Web、Windows 和 Android 新增功能已通过定向验收；Android 真机补测不重复付费调用。见 [AI 对话说明](docs/ai-conversations.md) 和 [额度基础](docs/ai-foundation.md)。

> **已验收的业务与生产基础**
>
> 已完成多用户业务、旧版迁移、邀请注册、邮件验证、PWA 安装和移动端适配。普通业务写入现已支持 IndexedDB 离线队列、服务端幂等重放与 409 冲突停靠；计时类操作仍严格要求在线。
>
> Tauri Windows 与 Capacitor Android 共用业务页面和 API，使用平台会话存储及可信更新源。0.1.3 双端本地应用内更新已人工验收；0.1.4 Windows 正式更新源已发布。新版本的覆盖安装与应用内更新不能互相代替验收。
>
> 已分离周期计划、目标路线和今日待办的工作区。新增公开产品首页、网页版入口、受发布清单控制的 Windows 下载与 Android 二维码，以及独立待办的日期、完成状态、编辑面板、分页、回收站和离线队列。当前阶段详情见 [官网与待办](docs/product-website-and-todos.md)。上一阶段 Windows 独立 EXE 与 Android 真机的 11 项回归记录见 [路线阶段](docs/route-stages.md)；本阶段不以浏览器手机视口测试代替 APK 真机验收。
>
> 阿里云使用独立 Compose 项目、MySQL 卷与回环 43140 端口。管理员权限持久保存，一次性初始化授权已关闭。0.1.4 已验证 MySQL 8.4 的 13 个迁移、用户隔离、409、幂等、备份恢复及 OSS 上传，双端正式包已通过云端只读验收。历史证据见 [云端发布交接](docs/cloud-release-014.md)，当前版本以发布记录为准。旧服务及备份继续保留。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Spring Security、OAuth2 Resource Server、Spring Data JPA、Flyway。
- 数据库：开发环境使用 H2 文件数据库；部署环境使用 MySQL 8.4。
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Vite 6。
- 工程化：Docker Compose、Nginx、GitHub Actions、Maven 集成测试。

项目当前使用 Spring Boot 3.5 系列；框架大版本升级应在独立分支验证接口、数据库迁移与客户端兼容性。

## 已实现

- 使用教学：六节分步练习、首次功能提示、可跳过与重学；练习数据不进入真实业务，进度按设备、服务器和账号保存。正式发布状态见 [教学说明](docs/learning-system.md)。
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
3. Android APK：已用 Capacitor 8 复用 Vue，Java 原生层负责受限 API 请求和 Keystore 会话加密；正式包仅向受邀测试者提供，应用内使用登录受限更新源，官网不公开APK。
4. 微信小程序：尚未开发，未来可单独实现页面层并复用API；不属于当前交付。

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
