# Java全栈学习与项目讲解路线

本页是阅读已实现代码的学习顺序，不是新的待开发清单。核对基线为2026年10月22日正式0.1.19／Android24；业务范围和未验证边界见[交付索引](stable-handoff.md)。用合成账号和隔离数据做断点练习，不调试真实邀请、邮件、密钥或私人计划。

## 一、解释一次认证请求

从 `auth/AuthController.java` 的注册或登录入口跟踪：参数校验 → 账号服务 → BCrypt／密码校验 → Repository → JWT与刷新会话。注册受邀请或部署开关限制，不能为了学习直接打开生产公众注册。

说明短期访问令牌、刷新令牌摘要、轮换和撤销分别解决什么问题；区分Web的HttpOnly Cookie与Windows／Android的平台会话存储。配合 `AuthPlanIntegrationTest`、`BootstrapIntegrationTest` 和 `auth/AccountRecoveryIntegrationTest` 阅读失败分支，不把“拿到JWT”当作完成账号安全。

## 二、跟通三类核心业务

从新建周期计划到完成任务，再对照目标路线和独立待办：

- Repository为什么必须验证 `ownerId`，子记录如何经父计划或路线验证归属。
- `@Transactional` 如何使进度与计时流水一同提交，失败后为何不能只保存一半。
- `@Version` 如何阻止手机用旧数据覆盖PC，409与网络错误如何区分。
- 路线阶段、顺序和前置依赖为何由完整服务端关系校验，而非只看当前页。
- DTO为什么不直接暴露JPA实体，Flyway为何不能换成生产 `ddl-auto=update`。

对应阅读 `RestDaysIntegrationTest`、`RouteStagesIntegrationTest`、`TodoIntegrationTest`。旧版JSON导入已经实现，其边界在[旧版迁移](legacy-migration.md)；旧回收站迁移仍是[对等清单](feature-parity.md)中的独立待办，不能笼统说全部历史内容都已转换。

## 三、复现一个409和一次安全重试

用隔离账号让两个客户端读取同一版本：A保存成功，B保存旧版本被拒绝。沿 `web/src/offlineQueue.ts`、`web/src/queueSync.ts` 和同步页面观察原操作如何保留、后续操作如何停靠、用户怎样基于最新云端版本确认重提。

再对照 `OfflineSnapshotIntegrationTest`、`OrchestrationIntegrationTest`、`PersonalTemplateIntegrationTest`：

1. 传输层24小时幂等缓存，与持久业务操作键有何不同。
2. 同键不同内容为什么不能重放；结果未知为什么不能直接换键重试。
3. 结构复制／模板应用为何要重映射ID、不复制执行历史；预览后源数据改变为何整批409。
4. 私有备份预检为何只读，恢复为何建立新记录、不覆盖正在使用的数据。

离线队列与字段差异界面已经实现，不再作为“以后再做”的简历承诺。AI、计时、账号安全等仍不参加自动离线重放；有共同基础才能称三方比较。

## 四、解释AI功能而不是只会调用接口

从 `ai/AiController.java` 跟踪用户同意、模式确认、任务创建、次数预占、工作池、结构化结果和确认应用，再读 `ai/AiWorkbenchController.java` 的独立草稿保存。用假上游测试学习，不消耗真实DeepSeek额度。

重点解释：密钥为何只放服务端；超时未知为何不自动退款／重试；模型输出为何必须经过所有权、版本、数量、引用和历史保护校验；本机自动保存为何不等于后台自动付费。复盘分析与调整入口的区别见[日周复盘](reflection-workspace.md)。

## 五、理解已具备的工程保障

当前CI按 `scripts/release-test-scope.json` 选择新增及受影响测试，重大回归才显式选full。查看 `.github/workflows/ci.yml`，不要把“计划使用Testcontainers”讲成已经在CI运行真实MySQL。真实MySQL迁移、恢复和旧镜像兼容由独立演练脚本及发布记录提供证据，与H2单元／集成验收分开。

登录失败保护已实现；当前运行结构没有Redis，不为了技术栈数量加一个Redis层。未来若测得多实例限流需求，再另立设计和验收范围。OpenAPI为部分历史契约，新增控制器索引和失败语义见[核心API](core-api.md)，不要宣称全部接口已有自动生成完整契约。

最后阅读[发布流程](release-process.md)与[0.1.19记录](release-0119.md)，能够区分签名／哈希校验、覆盖安装、应用内更新、原会话保留、空队列与非空队列测试。构建、手机网页视口和真机验收是不同证据层次。

## 面试表达

可以从事实出发简述：

> 我将原本的单机规划工具重构为多用户平台，使用Spring Security与用户归属查询隔离数据，利用JPA乐观锁、用户范围幂等和离线队列处理跨端写入；AI生成和修改先经过结构校验及用户确认，再原子应用。Web与Windows／Android共享业务页面，数据库迁移、独立恢复和签名升级分别有验证记录。

按自己真正理解和参与的部分调整措辞，说明AI工具辅助及自己的需求、复核和验收工作。不编造独立研发、团队规模、用户量、QPS或商用SLA；3—5分钟演示见[项目讲解](project-showcase.md)。
