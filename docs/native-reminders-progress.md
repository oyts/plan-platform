# 提醒阶段续做记录

更新时间：2026年10月20日。生产仍为0.1.17／Android22；本阶段源码未正式发布，不向更新源发布独立debug包，不改变公开注册、官网APK或现有AI额度。

## 已完成的实现

账号规则、设备选择、V26开始证据、最小后台授权、实时条件检查、防重、限流、免打扰、隐私默认、主设备、备份v1兼容及v2开始证据均已实现。共享页面沿用Swiss蓝白视觉，已查看320px真实浏览器截图，无横向溢出。Windows任务、Android原生Alarm及离线队列抑制已接入。最新追加了在线变更后撤回、服务端有效通知标识和原生历史清理。

## 已取得的证据

- 原提醒候选后端29项通过：提醒9、限流3、数据中心10、日常体验7。JAR在`backend/target-reminders/plan-platform-api.jar`，报告在同目录`surefire-reports`。H2验证不是MySQL演练；后续时间修正的验证范围见下文。
- 前端提醒及账号／队列／数据中心25项通过；API直接受影响15项通过，共40个独立用例。Windows提醒Rust4项通过。
- 浏览器4个场景乘桌面／手机视口共8项通过；409响应在浏览器中由模拟API提供，真实409另外由后端用例验证。证据在`acceptance-results/reminders/browser/`。
- Windows独立安装包修复了Task Scheduler XML必须使用UTF16LE BOM的真实兼容问题。系统任务实际唤醒，LastTaskResult为0，通知API接受合成通知，状态随后为SYSTEM_SETTING_0；未把该结果称为肉眼看到横幅或完整业务UI通过。证据已复制到`acceptance-results/reminders/windows-probe/`。自有测试任务已用`--reminder-clear`清理，正式计划舱不动。
- Android独立包首次流式安装被系统拒绝，非流式安装成功；最新版独立debug APK SHA256为`c040a3c2913b9e2ea36be16865ced5d6100d464482487760a895997de4119c42`。真实Java登录、默认关闭、设备开启与通知权限已通过。首轮后台核对超时，失败证据保留在`acceptance-results/reminders/android/2026-10-19T23-16-26-626Z/`，不能算后台提醒通过。

## Android真机补验

维护者此前仅延期手机真机，随后已连接并授权与第四阶段复盘合并补测。最终复盘独立APK安装、真实Java接口、复盘草稿／版本比较／预览门槛已验证，见[复盘阶段续做记录](reflection-progress.md)。冻结与空通知的早期失败过程保留下述记录；最终结果见“最终补验与时间读取修正”。

## 尚未通过的设备检查

用户已允许新版独立测试包覆盖并自行开启精确提醒。第二轮读取到了原生核对时间，但未查到本应用的活动系统通知，后台通知断言失败，证据在`acceptance-results/reminders/android/2026-10-19T23-22-27-823Z/`。未降低断言。

诊断包SHA256为`ae0eb682608eee4318711fec0e912ec9493e959d5c266412700fb658b232b20f`，已独立覆盖安装成功。仅新增分类、HTTP状态及时间，不含Key或事项正文。脚本使用43150 API、43151故障转发、43152 Android CDP；只连接独立regression包。

第三轮等待时，系统再次对本包`REMINDER_TICK`记录`Reason=frozen`（设备时间07:26:56）；用户选择自行允许独立测试包后台运行／自启动。已停止本轮自有Node测试、43150隔离API及43151／43152 ADB转发，避免抢占系统设置页面。第三轮记录只包含前两组通过，不是最终PASS。等待用户明确回复“已允许后台运行并返回测试包”后重新验收；不得假设该选择已经完成。停止时未强行打开应用清理，下一次进入测试包先`reminder_clear`清理上轮的本地测试授权，再重新配置合成环境。

Android系统Alarm诊断出现过`Reason=frozen`；须按真实设备结果判断厂商后台冻结边界，不擅自修改系统保护、不把前台通知当作关闭应用后准点通过。若手机进入其他应用或锁屏，停止输入并交回用户。

2026年10月20日17:35补验：使用最终复盘独立包，已有通知权限和精确提醒授权均通过，但退到后台后的原生核对等待180秒超时。系统记录本测试包的`REMINDER_TICK`于17:35:29被标记`Reason=frozen`。证据在`acceptance-results/reflection/android/2026-10-20T09-34-53-118Z/report.json`；结果为FAIL，未进入后续断网关闭用例，未用前台补发替代后台断言。测试进程及自有转发已结束；下一次进入独立包先清理上一轮本地测试提醒授权。用户正被询问是否检查该测试包的后台运行／自启动设置，正式应用与系统保护保持不变。

用户17:39截图确认自启动和闹钟与提醒开启，返回测试包后使用`--reminders-followup`仅补提醒，不重复复盘。17:41:40仍记录冻结，180秒后台核对超时，见`acceptance-results/reflection/android/2026-10-20T09-41-08-659Z/`的报告及`background-observation.json`；测试服务和自有转发已清理，没有自动修改系统设置。随后用户提供电池管理截图并自行允许后台高耗电，下一轮系统唤醒成功；不能以自启动已开单独推断后台投递通过。

## 剩余发布门槛

1. Android最新独立包的后台活动系统通知、完成后原生实时核对撤回、断网关闭及WebView刷新保持关闭已通过。锁屏准点、完整进程冷启动、通知点击跳转和离线有待发通知时的独立抑制场景未在本轮设备检查中证明。
2. Windows最新独立包6组真实联测已通过，包含系统唤醒、实时API、系统接受通知、完成后下一次系统核对撤回及离线清理；Rust5项通过。证据`acceptance-results/reflection/pc/2026-10-20T10-11-37-811Z/windows-reminder-report.json`。正式账号未切换，不把独立包视为正式升级保留验收。
3. 复核在线刷新并发、原生请求总时限、异常及通知点击跳转；不要削弱失败断言来追求通过。
4. 独立MySQL V26迁移、旧数据开始证据回填、V27复盘扩展、47表整库恢复及实际旧镜像读取兼容已在第四阶段合并通过，见[复盘阶段记录](reflection-progress.md)。真实生产快照及发布切换仍须另行执行。
5. 统一正式候选版本、同身份签名打包、正式覆盖前重新确认用户已保存关闭，完成本阶段定向升级后再发布。当前未授权自动切生产或发布未验收原生包。

## 最终补验与时间读取修正

用户自行将独立包改为允许后台高耗电后，原生后台核对成功，但09:46和09:49两轮无通知。定位到原始JDBC TIME读取与Hibernate UTC写入映射不一致；仅测试配置未使用生产UTC映射，因而旧单元结果没有覆盖该组合。现改用有类型的Hibernate候选投影，保留原有权限、执行历史和条数限制。9项提醒测试已在北京时间与UTC的JVM中分别通过；新JAR为`backend/target-reminder-timezone/plan-platform-api.jar`，SHA256 `a2bd2d078a25fba3faf41b5e166a9f11b21553b65f94d01faf3f696fd164f41d`。该查询的MySQL双时区补验及47表恢复随后通过，报告`reminder-query-candidates/20261020T100823Z-be55c78a/report.json`；不以旧JAR报告替代。

最终独立Android报告`acceptance-results/reflection/android/2026-10-20T09-56-40-013Z/report.json`为PASS，共5组：真实Java默认关闭、既有通知／精确授权与设备开启、真实后台新鲜核对及活动通知、任务完成后原生核对撤回、断网关闭与刷新保持关闭。后台活动通知数为1，完成后为0；撤回用调试入口执行同一原生实现，不声称另一次系统唤醒。未用前台补发替代后台断言，未声称看到横幅或完成冷启动。测试提醒已关闭，服务及转发已清理；系统后台耗电设置由用户决定是否恢复。

## 续做命令

```powershell
cd E:\Ai\假期计划\plan-platform
$env:JAVA_HOME='E:\biancheng\JDK21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -f backend/pom.xml '-Dplan.build.directory=target-reminder-timezone' '-Dtest=NativeReminderIntegrationTest' '-DargLine=-Duser.timezone=Asia/Shanghai' test
mvn -f backend/pom.xml '-Dplan.build.directory=target-reminder-timezone' '-Dtest=NativeReminderIntegrationTest' '-DargLine=-Duser.timezone=UTC' package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action test -NativeTestFilter reminders::tests
# USB已授权、用户允许独立包安装并已核对APK哈希后：
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/android-reminders-acceptance.mjs --reminders-followup
```

0.1.18／23签名候选与云端归档已准备，新的MySQL查询补验通过（两种时区及47表恢复），见[发布交接](release-0118.md)。正式三端仍0.1.17，未切换或发布。自动续做保持关闭；剩余额度不超过10%时保存并通知，不设置自动任务，不使用重置券。
