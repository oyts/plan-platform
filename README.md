# Plan Platform

一个面向长期计划、任务计时和顺序主线的开源多用户平台。项目由原本的纯前端“计划控制台”重构而来，目标是形成可以学习、部署、开源并用于 Java 求职项目经历的完整工程。

当前版本：`0.1.0`（前后端分离基础版）

## 当前状态

> **第一阶段基线验收完成（2026-09-17）**
>
> 已完成 Spring Boot 集成测试、Vue 生产构建与本地真实 HTTP 验收；旧版 JSON 已支持预检、幂等关系型导入、迁移报告和批次回滚。账号系统已完成 HttpOnly 刷新 Cookie、令牌轮换、登录限流、会话管理、注册开关和跨用户越权测试；下一阶段为旧版业务功能对等迁移。

## 技术栈

- 后端：Java 21、Spring Boot 3.5.16、Spring Security、OAuth2 Resource Server、Spring Data JPA、Flyway。
- 数据库：开发环境使用 H2 文件数据库；部署环境使用 MySQL 8.4。
- 前端：Vue 3、TypeScript、Pinia、Vue Router、Axios、Vite 6。
- 工程化：Docker Compose、Nginx、GitHub Actions、Maven 集成测试。

选择 Spring Boot 3.5 而不是 4.x，是为了在保持官方稳定版本的同时，获得更成熟的求职项目生态和第三方兼容性。Spring Boot 3.5.16、4.0.7 和 4.1.0 均为官方当前稳定线；本项目后续可以单独建立升级分支验证 4.x。

## 已实现

- 邮箱注册开关、登录限流、15 分钟 JWT Access Token、HttpOnly Refresh Cookie、令牌轮换与多设备会话管理。
- 用户级数据隔离，任何计划、标签、任务和主线查询都包含所有权条件。
- 计划、标签、任务的增删改查与输入校验。
- 任务开始、暂停、完成、手工补录、验收结果、结果备注与计时流水。
- 今日控制台、日期范围统计、每日复盘。
- 顺序主线 CRUD；前一步未完成时，后一步由后端强制锁定。
- JPA `@Version` 乐观锁，多设备使用旧版本提交时返回 HTTP 409。
- Flyway 数据库版本管理；H2/MySQL 共用迁移脚本。
- 旧版 1.x JSON 预检、原始快照、关系型导入、幂等控制、迁移报告与批次回滚。
- Docker Compose 一键启动 MySQL、Spring Boot、Vue/Nginx。

## 本地运行

环境要求：Java 21、Maven 3.9+、Node.js 18+。

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

## Docker 部署

复制配置并填写强密码：

```bash
copy .env.example .env
docker compose up -d --build
```

访问：http://localhost:43127

本地 Docker 首次体验可临时设置 `REGISTRATION_ENABLED=true`，创建账号后再关闭。生产环境必须修改 `JWT_SECRET`、数据库密码和允许跨域的域名，并设置 `REFRESH_COOKIE_SECURE=true`；阿里云部署时建议使用 HTTPS、独立数据库备份、日志采集和安全组最小开放原则。

## 目录

```text
plan-platform/
├─ backend/              Spring Boot API
│  └─ src/main/java/
│     └─ com.oyts.planplatform/
│        ├─ auth/        认证与令牌
│        ├─ plan/        计划、任务、进度、统计
│        ├─ mainline/    顺序主线
│        ├─ migration/   旧版数据迁移
│        └─ common/      异常与基础实体
├─ web/                  Vue 3 Web 客户端
├─ docs/                 架构与学习文档
└─ docker-compose.yml
```

## 多客户端路线

所有客户端只负责界面与离线缓存，业务数据最终通过同一套 `/api` 接口进入服务器：

1. Vue Web：当前基准客户端。
2. Windows/macOS EXE：使用 Tauri 封装 Vue，复用 TypeScript API 层。
3. Android APK：先用 Capacitor 快速复用 Vue；需要深入原生学习时再迁移 Kotlin。
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
