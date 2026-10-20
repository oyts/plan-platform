# 复盘阶段续做记录

更新时间：2026年10月20日。第四阶段共享功能、Web、独立MySQL及本轮PC／Android复盘定向检查已完成；第三阶段的Android后台提醒仍受设备冻结限制，不能视为通过。正式Web、Windows、Android仍为0.1.17／22；没有切换生产、覆盖正式客户端或发布APK。

## 完成范围

实现三模式日周事实、分页事项、自述、私有本机草稿、版本冲突比较、一次轻量引导、日周提醒合并、只读AI预览与会话、两周门槛及14天冷却、另开已有对象调整和私有备份格式3。默认20次AI及管理员热配置保持不变。产品规则和接口见[日周复盘](reflection-workspace.md)。

## 已验证

- 最终候选后端66项通过：Reflection9、ReflectionAI6、Reminder9、PersonalData10、DailyExperience7、AIAdjustment14、AIConversation11。JAR为`backend/target-reflection/plan-platform-api.jar`，SHA256 `c52710649db03a2e163e66f4f86f479455dc0927e28cedc3ba024731f93e4167`。证据在`acceptance-results/reflection/backend-final.log`与对应surefire报告。
- 复盘及直接受影响前端41项通过，见`acceptance-results/reflection/frontend-final.log`；Android构建附带原提醒受影响集合44项通过，二者有重叠，不相加声称85个独立用例。
- 网页10个场景乘桌面／手机视口共20项通过，见`acceptance-results/reflection/browser-final/`及同名日志。复盘交互使用模拟API，数据中心使用本机真实候选API；真实权限、409与AI任务去重另由后端测试验证。已查看320px实际截图。
- 修复初始读取与快速输入竞态；离线草稿重开、读取冲突云端后保留本机输入通过。早期失败报告保留，数据中心桌面“更多”选择器按当前真实导航修正，没有降低断言。
- Windows提醒路径及任务XML等5项Rust测试通过，见`acceptance-results/reflection/rust-final.log`。
- Android最终独立debug候选已构建并在用户授权后非流式覆盖安装到独立测试包，安装及设备APK哈希核对成功。路径`acceptance-results/reflection/android-final/PlanCabin-0.1.17-debug.apk`，SHA256 `3de5fa42bb956361cfccda9d01b25e720ca5fbf6d0a8b4043559fc776d3378d5`；5项提醒路径Java测试通过。日期校验不依赖API26的java.time，兼容现有minSdk24。正式应用未覆盖，该包不用于发布。
- MySQL8.4独立V25→V27迁移、原进度投影不变、稳定开始证据回填、日周权限及409、格式3只读副本恢复／防重、47表整库恢复校验和实际旧0.1.17镜像只读兼容全部通过。证据为远端`reflection-candidates/20261020T061810Z-3c32df69/report.json`。合成库、恢复库、旧容器和备份保留，测试容器均停止；正式0.1.17健康和注册关闭不变。
- Windows独立包通过6组UI观察：真实原生接口读取、显式保存本机草稿、Ctrl+R后原会话与未提交草稿恢复、显式云端保存、AI范围预览及未同意时禁用建立会话、日周定时提醒默认关闭。证据为`acceptance-results/reflection/pc/2026-10-20T06-05-12-982Z/pc-ui-observations.json`；用户自行登录合成账号。Ctrl+R仅证明WebView刷新恢复，不是完整进程冷启动或断网测试；本轮未执行PC原生409和后台业务通知。
- Android独立包复盘4组业务通过：事实与云端记录读取、本机草稿离线刷新恢复且未写云端、云端版本变化后保留本机输入并人工比较／第二次显式保存、AI同意门槛与周趋势／提醒默认值。首轮`acceptance-results/reflection/android/2026-10-20T09-31-32-027Z/report.json`前三组通过，第四组因测试定位器失败；修正为实际范围下拉框的角色定位后，在`2026-10-20T09-34-53-118Z/report.json`补验第四组通过。不是一次全绿运行；原生版本变化检查未发出过期PUT，真实HTTP409由后端及网页证据支持。没有付费AI或真实邮件。

## 正在执行和待办

1. MySQL门槛已通过。首轮脚本恢复操作键格式错误被400拒绝；第二轮复制JAR时权限过窄，容器无法读取非敏感应用包。这两轮失败证据保留；第三轮使用正确UUID与私有目录内可读JAR完成全部检查。Key和数据库环境文件权限未放宽，未删除失败库，不用前两轮替代最终PASS。
2. 独立PC包已完成上述复盘UI检查。合成服务使用43153、故障转发43154和一小时自动退出保护；运行目录为`acceptance-results/reflection/pc/2026-10-20T06-05-12-982Z`，持久合成库与登录文件留在本机，不上传GitHub。正式账号和客户端未改变。
3. 用户已连接手机并授权补测。复盘定向检查通过；合并提醒补验在17:35:29被Vivo以`Reason=frozen`冻结，后台核对180秒超时，后续断网关闭用例未执行。系统通知和精确提醒权限存在并不等于后台放行。等待用户检查独立测试包后台运行／自启动设置，不代用户更改系统保护；上一阶段Windows唤醒探针仍不等于完整业务联测。
   用户随后提供截图，确认独立测试包“自启动”和“闹钟与提醒”开启并返回应用。仅提醒补验仍于17:41:40记录冻结，180秒后台核对超时，证据在`acceptance-results/reflection/android/2026-10-20T09-41-08-659Z/`；复盘业务未重复，测试服务和自有转发已清理。进一步电池／耗电管理选项等待用户截图，不能据此要求开启所有权限。
4. 正式同身份签名、版本统一、真实生产快照／恢复、升级数据保留和生产部署均未执行，发布前重新确认。未验收APK不进入受限源，官网APK与公开注册继续关闭。

## 续做方式

```powershell
cd E:\Ai\假期计划\plan-platform
$env:JAVA_HOME='E:\biancheng\JDK21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -f backend/pom.xml '-Dplan.build.directory=target-reflection' '-Dtest=ReflectionIntegrationTest,ReflectionAiIntegrationTest,NativeReminderIntegrationTest,PersonalDataIntegrationTest,DailyExperienceIntegrationTest,AiAdjustmentIntegrationTest,AiConversationIntegrationTest' package
# 重建独立PC包前先确认旧独立窗口已关闭，不动正式应用：
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action regression
# 相同已核验JAR和EXE才允许恢复原合成库：
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/reflection-pc-fixture.mjs --resume acceptance-results/reflection/pc/2026-10-20T06-05-12-982Z
# Android复盘已通过，不重复；后台设置变化后仅补提醒：
# --reminders-followup使用当前复盘APK/JAR，仅测试提醒，不重复已通过的复盘。
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/android-reminders-acceptance.mjs --reminders-followup
```

浏览器专用配置为`web/playwright.reflection.config.ts`，显式使用target-reflection JAR；Windows只通过电脑操作技能验收，认证由用户自行完成。自动任务保持关闭，剩余额度不超过5%时保存并通知。
