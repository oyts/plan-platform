# 核心接口与失败语义

基础路径为 `/api`。普通业务需要Bearer Access Token，管理员端点还需要ADMIN；公开健康、部分账号入口和专用设备授权的提醒轮询另按各自规则。公开ID不授予权限，子记录也必须验证归属。正式三端为0.1.19／Android24，本页于2026年10月23日对照实际控制器核对。[当前源码契约](openapi-current.json)覆盖190个显式MVC操作、声明DTO及已审查的动态正常响应，由`ApiContractTest`检测漂移，包含尚未发布的本轮接口。54处服务包装返回从实际泛型提取，14处动态响应按已审查白名单描述；私有备份预检／恢复包含格式1—4。Spring正则路径转为合法OpenAPI参数，原规则留在`x-spring-route`。三个旧版迁移JSON请求仍标`x-dynamic-schema`，`default`响应及错误状态不作穷尽保证；不是可以替代所有业务校验的完整规范。较早的[部分OpenAPI](openapi.yaml)保留历史参考；当前已发布能力见[0.1.19记录](release-0119.md)。

## 本轮历史问题接口（源码已实现，尚未发布）

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
