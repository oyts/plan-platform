# 核心接口与失败语义

基础路径为 `/api`。普通业务需要Bearer Access Token，管理员端点还需要ADMIN；公开健康、部分账号入口和专用设备授权的提醒轮询另按各自规则。公开ID不授予权限，子记录也必须验证归属。本页于2026年10月23日对照0.1.20源码核对，实际发布状态见[本版记录](release-0120.md)。[当前源码契约](openapi-current.json)覆盖190个显式MVC操作、声明DTO及已审查的动态正常响应，由`ApiContractTest`检测漂移。54处服务包装返回从实际泛型提取，14处动态响应按已审查白名单描述；私有备份预检／恢复包含格式1—4。Spring正则路径转为合法OpenAPI参数，原规则留在`x-spring-route`。旧迁移三个请求有明确的宽容接收约定及六版本标准字段指南；共同错误按实际来源分类，`default`响应和基础设施错误不作逐状态穷尽保证。不是替代所有业务校验的验证器。较早的[部分OpenAPI](openapi.yaml)仅保留历史参考。

## 历史问题接口（已随0.1.20发布）

| 方法与路径 | 请求／返回与安全规则 |
| --- | --- |
| GET `/migrations/legacy/{importId}/recycling-preview` | 本人COMPLETED批次；返回指纹、待补类型／ID／删除时间、父ID及版本，不返回当前任务正文。未知算法、父关系缺失409，他人404 |
| POST `/migrations/legacy/{importId}/recycling-supplement` | `{operationKey:UUID,fingerprint:64位小写十六进制,confirmed:true}`；同事务回执和新增快照，过期409且不写入。原批次回滚包括原导入及补迁。不是普通离线写入 |
| GET `/auth/email-change/status` | 本人请求状态、两侧验证标记、有效期和重试等待；不返回令牌；`enabled`与SMTP `configured`分开 |
| POST `/auth/email-change/request` | `{currentPassword,newEmail}`，密码最长200、新邮箱最长190；当前密码核对、独立两封邮件均被SMTP接受才建立请求。200不等于邮箱已更改；503不覆盖旧有效请求 |
| POST `/auth/email-change/verify` | 公开但需一次性能力：`{requestId:UUID,side:old或new,token:60—100字符}`；仅POST主动确认，成功204；无效／重复／过期401，限流429。GET链接不会消费验证 |
| POST `/auth/email-change/{id}/cancel` | 本人登录后撤销请求，204；不更改登录邮箱。已完成变更不可借此回退 |
| POST `/auth/email-change/complete` | 本人`{requestId,currentPassword,confirmed:true}`，两个邮箱已验证才204；原／新地址、密码指纹、账号状态和会话代次复核。竞争占用409，整批回滚；成功后旧JWT／刷新／找回／验证及提醒授权撤销 |

邮箱变更在生产仍默认关闭（`EMAIL_CHANGE_ENABLED=false`）。2026年10月23日已在独立H2候选完成本人双邮箱真实验收：仅发送两封验证邮件，两侧本人主动确认后最终变更、旧会话拒绝和新邮箱登录均通过；没有修改管理员正式账号，详见[历史收口记录](historical-closure-progress.md)。普通会话撤销和本机队列保留是不同概念；更改邮箱不删除计划，不自动清除／提交本机离线队列。账号必须用新邮箱重新登录，24小时离线身份边界不变。

新验证邮件把能力放在`/change-email#request=…&side=…&token=…`片段中。页面只在内存暂存并移除片段，用户点击后POST；不放URL查询、日志、普通幂等缓存或离线队列。打开验证页可不登录；最终改邮箱必须在原账号登录状态完成。

当前JSON的校验方法：在仓库根目录执行`mvn -f backend/pom.xml -Dtest=ApiContractTest test`。核对控制器／DTO变更后，才可用`-Dcontract.update=true`机械更新；不能以更新快照代替权限／事务测试。检查器不调用真实SMTP或AI，不把动态返回猜成真实用户样本。原始流式数据中心请求的严格重复字段、5MiB限额和schema4恢复规则仍见[个人数据中心](personal-data-center.md)；CSV、Markdown、APK等内容类型由各控制器运行分支确定，JSON中`*/*`不表示固定JSON响应。

## 整套模板接口（0.1.19已发布）

`GET /health` 增加明确的 `capabilities.structureTemplates=true`；旧服务器缺失时不能推断为支持。该声明不授予权限，也不代表OS安装或后台提醒已验收。

整套模板的列表、完整读取、捕获预览、确认保存、资料编辑、删除、应用预览和确认建立分别使用 `/structure-templates`、`/{id}`、`/capture/preview`、`/capture`、`/{id}/preview` 和 `/{id}/apply`。保存／应用要求明确确认、预览指纹和UUID操作键；版本冲突返回409，重试同键同内容返回原结果。旧单任务模板 `/templates` 不变。完整字段及限制见[个人模板库](personal-template-library.md)。

## 新增接口索引

| 方法与路径 | 用途与边界 | 实现 |
| --- | --- | --- |
| GET、POST `/feedback` | 本人反馈分页／创建，创建含持久 operationKey 与请求指纹 | `feedback/FeedbackController.java` |
| GET `/feedback/{id}`，POST `/feedback/{id}/replies` | 本人详情与补充文字，无附件 | 同上 |
| GET `/admin/feedback`、`/{id}`，PUT `/{id}` | 管理员处理，状态变更校验版本 | 同上 |
| GET `/data/backup` | 本人字段白名单快照，私有 no-store，最多 5 MiB／20,000 行 | `data/PersonalDataController.java` |
| POST `/data/preview` | 原始 JSON 备份只读预检，不修改业务 | 同上 |
| POST `/data/restore` | `{backup,checksum,operationKey,confirmed}`，确认后恢复为新记录 | 同上 |
| GET `/data/restores`、`/data/restored-weekly-reviews`、`/data/restored-daily-reviews` | 恢复批次和只读复盘副本，`page` 从 0 开始 | 同上 |
| GET `/data/export/{kind}` | `routes`、`todos`、`weekly`、`daily` 的私有CSV，中文BOM与公式注入防护 | 同上 |
| POST `/organize/copy/preview`、`/copy/apply` | 本人整套结构复制，日期变化必须明确，重映射 ID | `organize/OrchestrationController.java` |
| POST `/organize/batch/preview`、`/batch/apply` | 指定重复批次未来实例，保护历史，摘要变化返回 409 | 同上 |
| GET `/organize/routes` | `page,size,search,state`，服务端路线摘要分页 | 同上 |
| GET `/mainlines/{id}/steps/page` | `page,size,search,status,deleted`，分页不代替完整依赖校验 | `mainline/MainlineController.java` |
| PUT `/mainlines/{id}/paused` | `{paused,version}`，有正在计时步骤时拒绝暂停路线 | 同上 |
| GET `/todos/page` | `from,to,page,size` 等实际筛选，数据库分页 | `todo/TodoController.java` |
| GET `/sync/record?path=...` | 仅精确路径白名单，只返回本人最新 version 与可编辑 fields，no-store | `sync/SyncRecordController.java` |
| GET `/admin/operations/status`、`/alerts`、`/policy-audit` | 证据摘要／站内事件／规则审计，不返回备份正文 | `operations/OperationsController.java` |
| POST `/admin/operations/evaluate`，PUT `/policy` | 核对固定分类告警／保存数字预览规则，没有删除接口 | 同上 |
| GET `/admin/governance/config`、`/members`、`/audit` | 能力开关、成员分页、状态审计 | `auth/MemberGovernanceController.java` |
| PUT `/admin/governance/members/{id}` | `{version,enabled,reason}`，受治理开关控制，禁止管理自己与其他管理员；生产已按确认策略启用 | 同上 |

## AI工作台、提醒与复盘接口索引

| 方法与路径 | 用途与边界 | 实现 |
| --- | --- | --- |
| GET `/ai/saved-drafts`、`/{id}`，PUT `/{id}` | 本人独立规划材料，新增version=-1、更新匹配version；不等于聊天历史备份 | `ai/AiWorkbenchController.java` |
| POST `/ai/saved-drafts/clear`、`/{id}/resume` | 按编号和版本删除／跳过；重新同意后建立新对话，不主动调用上游 | 同上 |
| GET、PUT `/reminders/preferences` | 账号规则版本校验；不代为打开系统权限 | `reminders/ReminderController.java` |
| GET、POST `/reminders/devices`，DELETE `/reminders/devices/{id}` | 本人设备查询／登记／撤销；登记返回的授权不写普通幂等缓存 | 同上 |
| POST `/reminders/poll` | 专用 `X-Reminder-Key`，不是Bearer登录；限流、核对最新状态、按设备事件防重 | 同上 |
| GET `/reflection/facts`、`/items` | `kind=DAY\|WEEK,start`；items还支持mode、bucket和分页，不把三模式混算完成率 | `reflection/ReflectionController.java` |
| GET、PUT `/reflection/days/{date}`、`/reflection/preferences` | 本人日自述／复盘规则及版本校验；旧计划日复盘独立保留 | 同上 |
| POST `/reflection/guides/claim`，GET `/reflection/trend`，POST `/reflection/trend/{start}/decision` | 一次引导、两周趋势门槛及14天冷却 | 同上 |
| GET `/reflection/targets`，POST `/reflection/analysis-preview` | 本人对象选择与同意前预览，不调用上游 | 同上 |
| POST、GET `/reflection/analyses` | 建立只读分析会话／历史；付费发送仍走既有AI jobs协议，不能从分析会话应用规划 | 同上 |

表中的实现路径相对于 `backend/src/main/java/com/oyts/planplatform/`。布尔能力字段表示API实现存在，不等于管理员已启用运维、邮件或治理策略。独立草稿、模板应用、复盘云端保存、AI、设备授权与计时不加入离线自动重放；不能从“接口有幂等键”推导成“可以自动离线重试”。

## 结构复制示例

以下是合成请求模板，ID 必须换成当前本人记录；不要拿生产数据演示。

```json
{"kind":"PLAN","sourceId":"本人计划ID","name":"复制后的两周阅读","startDate":"2026-11-02","endDate":"2026-11-15","restDaysMask":64}
```

将此对象发送到 `/organize/copy/preview`。`restDaysMask` 的星期一至星期日对应 bit 0—6，64 为星期日。ROUTE 不强加日期。确认时发送下列包络到 `/organize/copy/apply`；摘要来自刚才的预览，操作键由客户端生成并在发送前保存，网络结果不明时不能换新键盲目重发：

```json
{"request":{"kind":"PLAN","sourceId":"本人计划ID","name":"复制后的两周阅读","startDate":"2026-11-02","endDate":"2026-11-15","restDaysMask":64},"checksum":"预览返回的摘要","operationKey":"客户端生成的UUID","confirmed":true}
```

## HTTP 结果与客户端责任

### 错误正文不是统一包络

`openapi-current.json` 的 `x-shared-failure-families` 按实际来源记录共同错误族，**不表示每个接口都会返回表中全部状态**，也不把过滤器错误附会成业务DTO。`ApiErrorContractTest`直接检查异常处理器和模拟Servlet过滤器输出，不调用真实数据库、邮件或AI。

| 来源 | 状态 | 已确认正文 |
| --- | --- | --- |
| `ApiExceptionHandler` 业务异常 | 400／401／403／404／409／429／503 | `{timestamp,status,message}` |
| Bean Validation 字段错误 | 400 | 另含 `fields: {字段名: 提示}`；不回显被拒绝的值，同字段保留首条错误 |
| `MemberAccessFilter` 会话撤销／账号状态查询不可用 | 401／503 | 仅 `{message}`，没有 timestamp 或 status 字段 |
| `IdempotencyFilter` 内容不一致／仍处理中 | 409 | `{status,message}`，没有 timestamp；不等于所有业务409 |
| 畸形幂等键／AI或复盘请求超限 | 400／413 | 使用 `sendError`；由容器处理，不保证JSON或上述字段 |
| Spring Security、HTTP解析、Nginx等 | 随实际原因 | 可能空正文、容器JSON或HTML；不承诺固定包络或穷尽状态 |

客户端以HTTP状态为准，先检查内容类型，再尝试读取可选的 `message` 和 `fields`；解析失败也要保留原输入，显示安全的通用说明，不能把错误HTML当成功数据。上述目录不是“全部错误分支已逐接口穷尽验收”，也不承诺数据库异常或反向代理错误一定被Advice捕获。

### 旧迁移原始JSON的兼容边界

三个旧接口仍接收 `JsonNode`，不应用新版私有备份格式1—4的Schema：

- `POST /migrations/legacy/preview` 对可解析的JSON返回预检报告；结构或版本错误可以是HTTP200且 `ready=false`，不能只按HTTP200认定可迁移。
- `POST /migrations/legacy/import` 先要求预检 `ready=true`，再由迁移事务解析日期、进度、引用等；预检成功仍不等于写入必定成功。本人相同内容的已完成批次返回原报告，其他已完成批次会409。
- `POST /migrations/legacy-snapshot` 保存原始JSON，**不要求** `ready=true`；不能据保存成功宣称已转换关系表。这里的“原始”是Jackson解析后再次序列化的JSON，不保证保留原文件空白或键顺序对应的字节。

旧版标准输入使用数值 `schemaVersion:1—6`、对象 `progress` 与 `reflections`；`planVersion` 缺省为unknown。缺少工作区、内置目录时预检产生警告，部分内容只能保留快照。工作区包含 `customPlans/customMainlines/taskTemplates/trash/deletionMarkers`；回收站至多1000项、删除标记至多10000项，由 `LegacyRecycling` 联合验证类型、编号、删除时间和父子引用。永久删除标记不伪造可恢复正文，也不删除当前新版数据。

三个请求的Schema明确使用 `x-intentionally-unconstrained-json`，不是尚未审查的 `x-dynamic-schema`：preview和snapshot确实能接收非对象JSON，import对错误输入返回校验结果而非网关Schema拦截。`x-canonical-legacy-envelopes`分别列出schema1—6的标准字段、进度／计时、复盘、目录、四种回收站快照和删除标记；允许保留未知字段，不给旧标识错误添加UUID约束。9项真实Servlet兼容用例覆盖六版本缺省历史字段、重复导入、未知字段保留，以及三类非对象输入的预检／保存／导入差异。该字段指南不是声称严格枚举Jackson所有类型转换或每个历史导出文件；不新增会拒绝原有备份的运行时验证器。这是有意的兼容边界，不再当成等待收紧验证的产品缺陷。

| 结果 | 客户端处理 |
| --- | --- |
| 400 | 保留输入，显示字段、关系、大小或不支持操作的具体校验信息；不自动重试 |
| 401 | 会话过期或撤销；不自动提交旧队列，重新登录后仍按同账号／服务器隔离 |
| 403 | 权限不足；不要循环刷新凭据或把它显示为普通断网 |
| 404 | 对象不存在或不属于本人；不能由此推测他人数据 |
| 409 | 版本、摘要、操作键或业务状态变化；保留草稿，重新读取并由用户确认 |
| 429 | 限流／额度；等待或联系管理员，不绕过额度生成新操作键 |
| 503／无响应 | 保存可保存的本机草稿；AI 未知结果不自动重复付费，普通写入按原操作键核对 |

`X-Request-ID` 用于随机关联，不携带令牌、私有路径或正文。可排队普通业务的传输幂等为 24 小时；恢复、编排和 AI 应用的持久业务防重另有数据库约束，两者不能混为“所有请求永久 exactly-once”。
