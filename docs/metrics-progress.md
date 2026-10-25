# 核心阶段4：轻量统计与运行可视化

2026-10-25 22:21（北京时间）启动；基线7cd152d，正式三端0.1.23／Android28／V33，工作区干净。本轮在整阶段完成、5小时额度剩余10%、北京时间23:50中最先到达时停止；不启用自动任务。PC／Android操作先通知并等维护者确认。

范围：按北京时间注册增长；官网自愿参与的PV与按日去重访客估算；复用AI用量并补尝试／等待口径；仅含类别／状态／耗时的Nginx增量聚合、CPU／内存与本机健康探测；有新鲜度、表格替代及持续窗口／恢复阈值的管理面板；复用持久去重告警，新增真实发信默认关闭。公开注册、APK、私有正文、IP、UA与任意URL不进入指标。首次采集不补造历史。

实施约束：自愿分析默认关闭；公开采集不附业务凭据，固定页面枚举、短期签名票据、持久去重、全局限速及日容量上限；短期去重最长48小时、日汇总90天，删除只针对本模块到期记录。UV多日只能日去重合计。生产采集／发信／发布须届时确认；不修改线上配置做测试。整阶段开发后集中测新增和直接受影响功能。

当前状态：阶段4源码候选和本地定向验收中，尚未原生验收或发布。完整口径、阈值、保留规则、部署门槛见[轻量指标说明](lightweight-metrics.md)。

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
