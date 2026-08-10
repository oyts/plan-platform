# Java 全栈学习路线

建议不要只背代码，而是按下面顺序亲手调试。

## 第一阶段：能解释一次请求

从 `POST /api/auth/register` 开始，依次跟踪：Controller 参数校验 → Service 事务 → BCrypt → JPA Repository → Flyway 建表 → JWT 返回。要求能够说明每一层为什么存在。

## 第二阶段：掌握核心业务

从“新建计划”到“完成任务”完整断点调试：

- Repository 为什么必须带 `ownerId`。
- `@Transactional` 如何保证计时与流水一致。
- `@Version` 为什么能阻止手机覆盖 PC 的新数据。
- DTO 为什么不能直接暴露 JPA Entity。
- 为什么生产环境使用 Flyway，而不是 `ddl-auto=update`。

## 第三阶段：补齐工程能力

1. 给主线锁定规则增加集成测试。
2. 使用 Testcontainers 在 CI 中运行 MySQL 测试。
3. 增加 OpenAPI 文档与统一分页结构。
4. 增加 Redis 限流和登录失败保护。
5. 完成旧版 JSON 关系型迁移器。
6. 实现离线操作队列和字段级冲突响应。

## 面试表达模板

不要只说“用了 Spring Boot + Vue”。可以这样描述：

> 我把一个 LocalStorage/PWA 单机计划工具重构成多用户前后端分离平台。后端使用 Spring Security JWT 做无状态认证，用 Repository 所有权查询隔离用户数据，用 JPA 乐观锁解决多设备并发覆盖，并用 Flyway 管理 H2/MySQL 双环境表结构。前端保留离线优先方向，后续通过带基础版本和幂等键的操作队列实现多端同步。

面试前必须能现场解释一个 409 冲突是怎样产生的，以及为什么不能让后写入的数据无条件覆盖先写入的数据。
