# 核心阶段4：轻量统计与运行可视化

2026-10-25 22:21（北京时间）启动；基线7cd152d，正式三端0.1.23／Android28／V33，工作区干净。本轮在整阶段完成、5小时额度剩余10%、北京时间23:50中最先到达时停止；不启用自动任务。PC／Android操作先通知并等维护者确认。

范围：按北京时间注册增长；官网自愿参与的PV与按日去重访客估算；复用AI用量并补尝试／等待口径；仅含类别／状态／耗时的Nginx增量聚合、CPU／内存与本机健康探测；有新鲜度、表格替代及持续窗口／恢复阈值的管理面板；复用持久去重告警，新增真实发信默认关闭。公开注册、APK、私有正文、IP、UA与任意URL不进入指标。首次采集不补造历史。

实施约束：自愿分析默认关闭；公开采集不附业务凭据，固定页面枚举、短期签名票据、持久去重、全局限速及日容量上限；短期去重最长48小时、日汇总90天，删除只针对本模块到期记录。UV多日只能日去重合计。生产采集／发信／发布须届时确认；不修改线上配置做测试。整阶段开发后集中测新增和直接受影响功能。

当前状态：阶段4源码、Web／独立PC及独立MySQL／Linux已验收；Android真机由维护者延期，尚未正式发布。以下最新检查点优先于历史记录。完整口径、阈值、保留规则、部署门槛见[轻量指标说明](lightweight-metrics.md)。

## 2026-10-26 当前检查点（优先于下面历史记录）

维护者授权继续至阶段完成或5小时额度剩余10%，本次没有23:50截止、不设置自动任务。当前源码、Web、独立Windows和独立MySQL／Linux已验收；维护者明确选择“稍后再做手机验收”，本轮未枚举／安装／操作Android设备。**因此阶段4尚未整阶段交付；正式三端仍0.1.23／28／V33，未改线上配置、入口或下载源。**

### 本次完成与证据

- 代码补齐：公开分析数据库／事务异常明确返回503＋no-store＋空正文，不经过认证错误跳转、不泄漏SQL；关闭采集不把现存故障误报恢复；采集器原子临时文件不被旧.next阻塞；回退增加metrics能力守卫；日志轮转使用已核对的oyts主组，原operations组101只读挂载保留。
- 后端9类54项通过，按最终JUnit XML组合计数，不是把多轮重复相加。主要日志：`acceptance-results/metrics-public-unavailable.log`（11项指标集成＋10项契约）、`metrics-alert-boundaries-final.log`（10项运行窗口＋运维受影响用例）、`metrics-transaction-failure-final.log`（2项存储／事务边界）；其余AI统计等已有证据在上轮。没有重跑三模式、计时等无关全量业务。
- Web最终14项通过（6项新增＋8项直接受影响官网），记录`acceptance-results/metrics-browser-and-home-final.log`；测试最后自动停止Java和预览，43180／43130端口释放。Windows对子进程信号的限制由只在测试脚本使用的回环随机端口／随机nonce退出通道处理，不加入正式API或安装包。前端分析单元6项沿用未变源码结果；采集7项、配置5项、回退7项再次通过。
- 独立PC6组实际UI检查通过，记录`acceptance-results/metrics/native/pc-observations-20261026.json`。使用电脑操作技能，本人登录；窗口EXE SHA256 `ca2a4f9acb81b19d360e42af2a969135d986d5833a43f20848964efc2d4a9d87`。检查7日／昨日、图表和同源表、运行采样／阈值／邮件关闭、AI尝试和未知等待、长页及冷重开登录保留。未改正式客户端、未发送模型或邮件、未同步真实队列。该UI轮服务JAR为cc3e5628；后续仅后台错误／停用边界修正由专用后端用例和最终JAR验证补证，不冒充原UI重新验收。测试配置健康版本仍是历史0.1.7，不代表正式服务降级。
- 独立MySQL8.4：`/home/oyts/plan-platform/metrics-candidates/20261026T011449Z-daa2c3f4/`，58张原表的原字段投影不变，真实V33旧镜像读取兼容；PV／UV并发重放／白名单／成员403；公开存储失败原子回滚与同票据重提；真实定时48小时过期，日聚合和4个合成账号不删；最终完整62表恢复校验、回退守卫通过。原始失败报告保留：第一轮测试解析预期403纯文本失败；第二轮发现通用错误转401，修产品后从失败点续验，未删除标记重刷迁移。综合`final-report.json`6组＋承接原2组分别记录，不声称一轮不中断全过。
- Linux：实际Nginx语法通过；最终采集器SHA256 `3e8615b85236c332ff158c14567f2b44ef1dddd5e702383f241ce3dacbd5bc0a`，合成21000行、单轮20000行0.1189秒墙钟／0.1172秒进程CPU，RSS19156KiB。限额480MiB／0.6CPU测试容器、4个合成账号、各接口预热后5次样本：growth／website／runtime中位数95.337／86.316／29.683ms。不是生产压测或高并发承诺，采集基线用合成就绪结果，不把它当公网探测证据。真实Linux合成日志轮转通过；postrotate用true，没有向生产容器发重开信号。
- 完整恢复轮JAR `ce2adb4c6337b5b60f4119a408f4a2337d6d4f7999c627505d4cdd31f490f763`；最后停用边界／事务异常JAR `628318be55fe44c2b72d7e3db4c358491182fc98e1f5b4975c84dbc620d3b7ef` 在同一独立V34库补启动、62张表checksum不变，通过`verify-628318be.json`。两份证据不混成“最终JAR从头跑过迁移”。本地报告在`acceptance-results/metrics/cloud/`，服务端合成库、镜像、备份和停止的测试容器保留。
- Android独立APK已构建及哈希核对：`.desktop-tools/native-regression/PlanCabin-0.1.23-debug.apk`，SHA256 `48282720e7a51758a129a580b89384458c8b2dc0f30c1251860b768ed1d143b4`，独立包名`cn.zijun701.plan.mobile.regression`。**仅语法检查了`android-metrics.mjs`，尚未安装或跑设备，不可提供给朋友／发布到更新源。**
- 检查清单与CI已改为本阶段新增／直接受影响范围，使用专用浏览器配置和构建目录，默认不跑旧AI规划全量或全部Rust测试；真正大版本仍可显式full。工作流本地结构／清单校验不冒充GitHub运行通过。

### 下一步，仅保留必要门槛

1. 等维护者明确连接并允许手机测试。先核对真实额度／Git，再确认本地43180空闲，恢复原合成库（不是正式账号）：`node scripts/start-metrics-fixture.mjs --resume acceptance-results/admin-center/2026-10-26T01-11-49-715Z`，交互终端保持stdin打开。用`prepare-metrics-native.mjs`更新最终API／EXE／APK哈希。
2. 用户允许后才枚举唯一设备，核对包名及APK哈希，再同包名覆盖**独立测试包**（非正式应用）；系统安装／隐私提示由本人处理。运行`node scripts/android-metrics.mjs`，预期3组：增长范围／表、资源与阈值、AI元数据／成员拒绝／冷重开。不能将脚本已写当作通过。若采集模拟文件缺失，按fixture工具恢复，不读真实计划填数。
3. Android通过后再确认正式0.1.24／构建29候选、签名、当时生产V33快照／V34迁移恢复、生产采集与自愿分析启用、正式双端保留升级及发布。新的真实告警邮件默认关闭，启用需再次核对。**没有正式覆盖安装或生产发布授权，不替维护者推定。**
4. 新版上线后回退旧0.1.23必须经过新的metrics守卫，已有日聚合或新类待发邮件时不能用旧后端停止到期清理。暂不进入阶段5，不推进UI大重构。

本次实际新增付费AI请求0、真实邮件0、正式应用／数据库／入口变更0。手机延期不伪报整阶段已完成，也不会用剩余额度开展无关功能。

## 2026-10-25 夜间检查点

已实现：V34三张独立分析表和运维邮件摘要列；管理员注册增长／自愿PV与按日UV／AI尝试及等待／运行资源和请求类别；官网主动勾选的无凭据分析；1MiB／20000行／2秒增量采集、轮转缺口未知、持续触发与恢复窗口、独立邮件开关和30分钟去重；受限部署模板及客户端下载配置防丢失守卫。正式网站、数据库、签名包、更新源均未改，默认采集和新增发信关闭。

实际验收（合成数据，无收费AI／真实邮件）：

- 后端本轮8类46项通过，记录`acceptance-results/metrics-backend-verified.log`；后续就绪恢复边界使RuntimeTelemetryTest由6变7，`metrics-telemetry-final2.log`通过。新增AI尝试／修复／已知Token／10秒等待专用断言，最终结果以`metrics-ai-summary-final.log`及对应JUnit XML为准，不能只凭命令已启动算通过。
- 前端`src/publicAnalytics.test.ts`6项通过；前端最终构建`metrics-web-build-final2.log`通过。
- `scripts/test-runtime-telemetry.py`6项、`runtime-metrics-config.test.mjs`4项通过。包含Windows CRLF、增量重读／部分行、轮转、错误行缺口、隐私字段注入拒绝、限额、日志白名单和静态发布保留守卫；不是Linux Nginx执行证据。
- 浏览器`playwright.metrics.config.ts`6项全部通过，`acceptance-results/metrics-browser-final6.log`（desktop-web3／mobile-web3）。核对增长范围、图表／表格、默认不参与／主动参与／退出、无Cookie／Authorization、运行新鲜度、独立邮件关闭、普通成员拒绝。桌面／手机视口截图在`web/test-results/metrics-*/metrics-*.png`，已实际查看手机运行页，无页面级横向溢出，宽表仅容器内滚动。
- 早轮明确发现并修复：H2的day保留字别名、Windows日志换行、Capacitor对象存在不代表原生平台（导致官网入口被误藏）、跨用例合成PV使图表数量增加（改为精确选择注册图，未削弱产品断言）、服务恢复需要连续两次通过。失败证据保留，不宣称首轮全过。
- API契约已按新增6个接口与AttemptSummary更新；两类公开输入为严格白名单而非泛JSON，正常响应保留实际DTO；契约10项已在上述后端集合通过。

最终JUnit XML汇总：8类共48项、0失败／0错误（RuntimeTelemetryTest7、AiUsageIntegrationTest6，其余上轮不变）。这是本轮最终源码的组合定向证据，不声称最后一次单命令重跑了48项。网页六项断言全过，但Windows子进程退出仍需精确清理，不能把这次测试工具生命周期问题当作已修复；后续原生／部署门槛保留。

最终收尾核对：AI专用6项进程退出0；浏览器最终进程退出0并显示`6 passed (3.5m)`，不是仅采信中途控制台。已按精确类名／合成目录及Vite配置／端口停止本轮遗留Java／预览服务，未操作正式应用。源码候选提交`68eb3a9`已推送origin/main，签名发布和生产仍未执行。暂不启动独立PC、也不要求用户提前登录；下一次从构建独立包及MySQL授权开始。5小时额度最后读数已用89%，先完成本收尾，不启动可能越过剩余10%线的新构建；不设自动任务。

### 准确续做顺序

1. 先核对Git／5小时额度／北京时间。本轮规则仍是剩余10%或23:50先到即停；不设自动任务。
2. 读本文和`lightweight-metrics.md`；核对最终AI专用断言结果，将确切测试数写回。后端最后变化只有持续恢复边界和专用AI断言，应定向复验，不重跑无关业务。
3. 尚未构建独立PC包。用户已回复“可以，准备好后通知我登录”，这不是已经登录或已经验收；构建后读取电脑操作技能，启动独立测试包，再请本人用新合成凭据登录。不得覆盖正式0.1.23或自动填写Windows认证。Web／PC通过后再询问Android连接安装。
4. 本阶段ECS独立MySQL／Linux性能演练问题尚未收到第二项答复，不沿用阶段3的单次授权。收到批准后新建合成V34隔离库和受限容器，做V33业务保护、恢复、兼容与真实小机预算，不读原访问日志、不挂AI／SMTP密钥。
5. `start-metrics-fixture.mjs`以测试classpath、43180服务、`acceptance-results/admin-center/<ISO>`新库启动，无SMTP／真实AI。临时Windows网页测试清理可能留下Java／Vite子进程；只允许按MetricsFixture类名与合成目录、或vite.admin preview端口43130核对后结束本轮进程，不能杀正式程序。正式隔离演练必须补进程生命周期自动清理门槛。
6. 未准备可发布JAR／EXE／APK；当前源码仍0.1.23，不允许以同版本发布不同包。正式生产部署、签名候选及双端保留覆盖须届时批准，之后才发布可选更新。公开注册、官网APK保持关闭。

### 可复现本地命令

项目根设置JAVA_HOME为`E:\biancheng\JDK21`，Maven为`E:\biancheng\maven\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd`：

```powershell
mvn -q -f backend/pom.xml '-Dplan.build.directory=target-metrics' '-Dtest=MetricsIntegrationTest,RuntimeTelemetryTest,AiUsageIntegrationTest,OperationsMailDeliveryTest,GovernanceOperationsIntegrationTest,GovernanceDisabledIntegrationTest,ApiContractTest,ApiErrorContractTest' '-DargLine=-Djava.io.tmpdir=E:/Ai/假期计划/plan-platform/acceptance-results/metrics-temp' test
mvn -q -f backend/pom.xml '-Dplan.build.directory=target-metrics' dependency:build-classpath '-Dmdep.outputFile=target-metrics/classpath.txt'
.desktop-tools/public-web/venv/Scripts/python.exe scripts/test-runtime-telemetry.py
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe --test scripts/runtime-metrics-config.test.mjs
```

进入web，Node22加入PATH，PLAYWRIGHT_BROWSERS_PATH设项目`.desktop-tools/playwright`，先确认43130和43180均非其他任务使用：

```powershell
npm run build
node node_modules/vitest/vitest.mjs run src/publicAnalytics.test.ts
node node_modules/@playwright/test/cli.js test --config playwright.metrics.config.ts
```

不能把此处浏览器手机视口当Android APK真机通过，也不能把H2 V34当MySQL迁移恢复通过。
