# 核心阶段1 AI可靠性开发与验收进度

更新于2026年10月24日。正式三端已发布0.1.21／Android26／MySQL V31，生产快照与正式设备证据见[0.1.21记录](release-0121.md)。本页开发证据以31c2c1a及ad05292为基线，不推进阶段2—6或UI重构。维护者本轮改为剩余5%停止，不自动安排续做。

**当前结论：阶段1开发、约定定向验收及0.1.21正式发布已完成。** 下文未发布0.1.20候选是历史开发检查点，不替换历史标签；朋友使用已验收的0.1.21正式包。

## 10月24日补验

- 原合成数据库按受限 `--resume` 路径恢复，没有重建库或更换维护者登录凭据。PC补完手改保存、逐次诊断、明确修订、关闭重开和确认未恢复：通过。未执行真实计划创建，未碰正式客户端。旧PARTIAL记录保留，补验记录为acceptance-results/ai-reliability/pc/observations-20261024.json。
- Android真机首次发现需求笔记保存被后续sticky输入表单遮挡。按frontend-design技能检查真实截图，保持Swiss蓝白和现有页签，仅在手机尺寸展开笔记时取消后续表单悬浮；不是全站视觉重构。补上500px短视口下保存按钮真实命中检查。
- 最终Web新增6项重新通过；不是新增6个不同案例，也未重跑无关旧业务。证据：acceptance-results/historical-closure/ai-reliability-browser-notes-fix3.log。前两次运行因浏览器路径／受限进程及残留预览端口失败，失败日志保留，改用仓库浏览器和原用户权限后通过。
- Android独立包最终SHA256：ff23b8d601c0ebd953944293fb8eb0044d6c3844b5737181c9a1fdb32071fdfa。五组通过：只讨论／笔记与未保存保护；一次修复两次诊断及计次；手改保存和二次修订；冷重开保留；两次确定失败时旧草稿、可读文字及退款。真实Java桥接，本机H2／假上游，不是页面拦截模拟。正式APK前后哈希相同。证据：acceptance-results/ai-reliability/android/2026-10-23T19-14-18-904Z/report.json。
- 首次安装被系统取消，维护者处理后同独立包安装成功。脚本HTTP状态码、成功诊断reason=null时读取state的两处错误已修正，旧失败报告保留；OEM WebView的CDP截图超时改用前台守卫后的真实屏幕截图，不绕过业务断言或安全弹窗。
- 最终独立PC重建SHA256：fa2011297282a455ec195a27711d9b08970f582a6805c8f0dee2ff03ab81042c。原合成登录／首页保留通过；最后一次AI入口只读补看遇到电脑工具坐标错误，由维护者打开原对话后，真实截图／UIA核对原三轮对话、修订标题、已用1／占用0、保存禁用和确认生成禁用均正常。记为有人机交接的补验，未用CDP或自动认证替代技能。
- 本次新增收费AI调用0、真实邮件0、生产部署0。此前有界真实模型7父请求／8上游／9847输出已结案，预算不重新获得。

下文10月23日记录为历史检查点；当前下一步以本节与页末为准。

## 已实现

- 将需求交流、生成、修订区分为DIALOGUE／GENERATE／REVISE；旧客户端省略意图时保留AUTO协议。讨论不会改草稿，模式仍须用户确认，生成和已有对象调整仍要预览并明确应用。
- 独立需求笔记保留早期约束，不随最近6轮上下文裁剪丢失。可整理和保存，不调用AI、不扣次；有未保存笔记时禁止发送、导入或应用，刷新不覆盖本机编辑。同时修改笔记和草稿时，先保存笔记也不会丢掉草稿。
- 提示规则区分时间弹性与缺失信息，每轮优先只追问1—2个必要问题；相对日期包含北京时间与会话建立日，日历月份不直接当60天；路线不强加期限、待办不扩成长计划。模型遵循程度仍须实际样本判断。
- V31持久尝试账本，每个父任务最多首调和一次修复，数据库主键与父任务锁防止并发／重开重放。仅有确定回复及用量、且输出验证失败时允许修复；MODE_MISMATCH、UNKNOWN、TIMEOUT、用量缺失不自动修复。最多去除唯一完整外层代码围栏，不补造业务内容。
- 同一预占在修复期间保持RESERVED，成功只计一次用户次数；两次明确失败才释放。未知结果不退款／自动再调。父请求汇总两次已知Token，部分已知明确标记不完整，不能当零成本。
- 两次共享默认60秒期限、合计8192输出Token及64K保守输入上界；修复只能使用剩余预算，且仍受原上下文限制。输入边界采用UTF-8字节保守估算，不是供应商精确Tokenizer。修复开关默认关闭，正式Compose尚未启用。
- 合法外层中的可读回复可保留为参考文字，无效草稿不替换旧草稿。用户看到修复中／失败／未知及请求编号；展开诊断可读每次尝试原因、字数、已知Token和耗时。编号与管理员请求账本对应。
- 管理员可按原因分类查看尝试数，含已被修复的失败尝试；不读取提示词或草稿。分类随30天会话删除，90天父请求用量汇总继续保留，界面分别说明口径。

## 最终共享源码定向验收

| 范围 | 结果 | 证据 |
| --- | --- | --- |
| 后端新增及直接受影响AI | 66项通过 | historical-closure/ai-reliability-stage-backend-final.log |
| 路由及错误契约 | 10项通过，191条路由快照已更新 | historical-closure/ai-reliability-contract-final3.log |
| 前端单元 | 13个不同用例通过；最后12项重跑通过，另1项此前已通过且源码未再改 | historical-closure/ai-reliability-frontend-final.log及stage-frontend-node22.log |
| 新网页流程 | 桌面／手机网页尺寸共6项通过 | historical-closure/ai-reliability-browser-accepted.log |
| 直接受影响旧AI网页 | 22项通过，不包含无关账号找回等用例 | historical-closure/ai-reliability-affected-browser.log |
| Web及独立Windows构建 | 通过，不等同原生UI验收 | historical-closure/ai-reliability-web-build-final.log及pc-build-final.log |
| 独立MySQL | V30→V31、50张原业务表投影不变、52表完整恢复、旧镜像启动后数据不变；4组通过 | historical-closure/ai-reliability-mysql-final.json |

所有相对证据路径以本机acceptance-results/为根，未纳入Git。H2及假上游三模式多轮案例不冒充真实模型质量。Java两个月、周日休息、学校弹性、吉他路线、零散待办、修复上限、未知／预算、所有权、409、幂等和保护历史均有合成覆盖。

最终候选API SHA256：6fa815851897c8852cdea8eb4a5cb6f38bbe461cbf46cb58ae9d8133710a20df。版本字符串暂留0.1.20，但不是已发布0.1.20包；不得覆盖历史签名产物或标签。独立MySQL目录：/home/oyts/plan-platform/reliability-candidates/20261023T152300Z-6fa81585。新库、恢复库、旧测试容器和备份保留，容器已停止；不是生产快照或生产升级。

## 本轮真实调用授权和进行中事项

维护者批准最多6个合成父请求、每个最多一次修复、总计最多12次上游调用、总输出49152 Token。scripts/ai-reliability-real.py只使用已通过MySQL门槛的同一JAR，在独立新库和回环43172服务运行。Key由Docker只读挂载，不由脚本读取。启动标记与每次提交前账本在服务器受限目录持久保存，失败不删除标记重刷。真实回复只留私有验收文件，不进源码。结束后默认额度改0并停止候选。

真实轮次已结束：6个父请求、7次上游尝试、已知输出9682 Token。5项严格内容检查通过：Java两个月54项／周日休息（一次修复）、学校弹性3项、吉他8步路线、待办3项和二次修改；四组可应用草稿明确应用及重放结果一致。首次讨论有文字建议但推荐码null，原报告保持REVIEW_REQUIRED，不改成全通过。

随后只调整固定提示的推荐规则和避免重复确认规则，24项直接受影响后端检查通过。最终JAR为1ef2adc76b176fc6feaddecab9aed027755bd5b8bf7ca6dfe6d1b916a1e177ab；其MySQL冷启动及31项迁移校验不变通过（不把前一个JAR的52表恢复说成新JAR重新恢复）。维护者另行批准1个父请求、最多2次上游／2048输出的补验，实际1次上游、165输出，明确推荐PLAN、draft=null，通过。原6请求报告与补验分别保存，不能合并成原轮6/6通过；补验输入也更明确表达了每日安排偏好，不将单个样本当线上成功率承诺。两轮合计7个父请求、8次上游、9847输出Token，已停止候选，额度设0，无追加授权余额可再发请求。

证据：acceptance-results/historical-closure/ai-reliability-real-final.json、ai-reliability-recommendation-final.json、ai-reliability-final-jar-mysql.json。没有给真实用户发信，没有切换Nginx/current或修改正式额度。

PC维护者已自行登录。独立EXE SHA256为57cbf739cf779dd166aa7e0165c5a1dea3d4445a5348345074ba8b62f37ac68d，已观察讨论成功且无草稿、原约束笔记、编辑笔记时禁止生成、保存笔记仍已用1次、生成后已用2次（修复不多扣）、合成Java草稿与周日休息、手改标题及未保存时禁止应用。尚未完成手改草稿保存、诊断展开、重开及后续修订；不能算PC整组通过。鼠标操作在自动滚动后存在落点不稳定，笔记保存通过键盘Tab聚焦后确认；不是因此更改产品或绕过认证。

本机假上游进程会话86855，数据库位于acceptance-results/ai-reliability/2026-10-23T15-25-05-949Z，启动器一小时自动停止。下次必须先核对进程／端口和合成库，不要另建新库覆盖原凭据。PC当前有未保存的合成标题“合成第一步－PC手工调整”；不要关闭／替换测试包或丢弃。合成登录文件仍为acceptance-results/ai-reliability/pc/synthetic-login.txt。Android尚未安装／操作本轮包，必须先完成PC再通知连接。

本窗口最后可靠额度已用88%，进入安全收尾；不启用自动任务，按剩余10%停止。阶段1尚未完整结束，正式版本仍0.1.20。

## 保留的失败证据与修正

- 默认Node18缺少全局crypto导致初轮前端失败；固定使用仓库Node22后通过。未修改产品来绕过断言。
- 测试Java进程及浏览器清理曾受Windows沙箱ACL限制，原用户权限下复验通过。
- 新网页fixture最初未将Cookie／CORS配置固定为本机验收值，浏览器落到登录页；修正隔离配置后6项通过。
- 两次契约命令参数／生成开关错误及随后契约漂移失败保留；按正确开关更新审查后的快照，最终10项通过。
- 首个MySQL演练未覆盖开发H2驱动，启动失败；第二轮迁移和API通过，但服务器Python3.6不支持gzip.compress(mtime=0)。改用GzipFile兼容路径后，仅续做恢复和旧镜像门槛，52表相同。未删库或篡改Flyway校验和。

## 当前收尾与发布边界

1. Web最终6项、独立Android5组、PC完整流程及最终重建包只读保留补验已完成；真实模型结论仍保留原5/6与单独补验，不改写为原轮全通过。
2. 文档／scope一致性和敏感审查后同步GitHub源码；阶段2—6本轮不开始。
3. 正式发布门槛已在0.1.21执行：同身份签名、真实快照／恢复与迁移、公网健康、正式升级保留及两条更新源；原0.1.20开发调试候选不再分发。GitHub归档状态见对应发布检查点，不移动任何历史标签。
4. 不重跑任何真实调用脚本，不删除启动标记；没有剩余收费授权。不自动安排任务。

## 定向复现（不调用收费上游）

PC与手机本轮证据使用同一受限合成库，启动器一小时自动停止。保留库续做仅限下列路径；不要重新运行seed或覆盖synthetic-login.txt：

```powershell
$env:JAVA_HOME='E:\biancheng\JDK21'
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/start-ai-reliability-fixture.mjs --resume acceptance-results/ai-reliability/2026-10-23T15-25-05-949Z
```

另开本机终端，使用Node22及仓库浏览器；`PLAN_RELIABILITY_REUSE=1`只用于已经确认的上述假上游：

```powershell
$env:PATH=(Join-Path (Get-Location) '.desktop-tools/node22/node-v22.23.3-win-x64')+';'+$env:PATH
$env:PLAYWRIGHT_BROWSERS_PATH=(Join-Path (Get-Location) '.desktop-tools/playwright')
$env:PLAN_RELIABILITY_REUSE='1'
npm.cmd --prefix web run test:e2e -- --config playwright.reliability.config.ts
```

手机需本人连接、解锁并允许独立包安装之后，才运行`node scripts/android-ai-reliability.mjs`。脚本要求唯一授权设备、当前精确APK哈希与独立包身份，遇到系统提示等待本人，不代点安全确认；会创建新合成账号，退出旧账号也只限独立包，不清数据。APK路径：`.desktop-tools/ai-reliability-android/PlanCabin-0.1.20-debug.apk`，**不是正式包，不发朋友或上传更新源**。未来重建哈希改变时必须重新审查及验收，不能去掉哈希断言。
