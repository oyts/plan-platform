# 提醒阶段续做记录

更新时间：2026年10月20日。生产仍为0.1.17／Android22；本阶段源码未正式发布，不向更新源发布独立debug包，不改变公开注册、官网APK或现有AI额度。

## 已完成的实现

账号规则、设备选择、V26开始证据、最小后台授权、实时条件检查、防重、限流、免打扰、隐私默认、主设备、备份v1兼容及v2开始证据均已实现。共享页面沿用Swiss蓝白视觉，已查看320px真实浏览器截图，无横向溢出。Windows任务、Android原生Alarm及离线队列抑制已接入。最新追加了在线变更后撤回、服务端有效通知标识和原生历史清理。

## 已取得的证据

- 最终后端29项通过：提醒9、限流3、数据中心10、日常体验7。JAR在`backend/target-reminders/plan-platform-api.jar`，报告在同目录`surefire-reports`。H2验证不是MySQL演练。
- 前端提醒及账号／队列／数据中心25项通过；API直接受影响15项通过，共40个独立用例。Windows提醒Rust4项通过。
- 浏览器4个场景乘桌面／手机视口共8项通过；409响应在浏览器中由模拟API提供，真实409另外由后端用例验证。证据在`acceptance-results/reminders/browser/`。
- Windows独立安装包修复了Task Scheduler XML必须使用UTF16LE BOM的真实兼容问题。系统任务实际唤醒，LastTaskResult为0，通知API接受合成通知，状态随后为SYSTEM_SETTING_0；未把该结果称为肉眼看到横幅或完整业务UI通过。证据已复制到`acceptance-results/reminders/windows-probe/`。自有测试任务已用`--reminder-clear`清理，正式计划舱不动。
- Android独立包首次流式安装被系统拒绝，非流式安装成功；最新版独立debug APK SHA256为`c040a3c2913b9e2ea36be16865ced5d6100d464482487760a895997de4119c42`。真实Java登录、默认关闭、设备开启与通知权限已通过。首轮后台核对超时，失败证据保留在`acceptance-results/reminders/android/2026-10-19T23-16-26-626Z/`，不能算后台提醒通过。

## Android真机延期

维护者于2026年10月20日要求仅延期手机真机验收，与第四阶段日周复盘一起补测。本轮不检查设备、不安装、不操作手机；下方冻结问题及失败证据保留，不能视为已解除。Web、PC和数据库验收继续。合并进度见[复盘阶段续做记录](reflection-progress.md)。

## 尚未通过的设备检查

用户已允许新版独立测试包覆盖并自行开启精确提醒。第二轮读取到了原生核对时间，但未查到本应用的活动系统通知，后台通知断言失败，证据在`acceptance-results/reminders/android/2026-10-19T23-22-27-823Z/`。未降低断言。

诊断包SHA256为`ae0eb682608eee4318711fec0e912ec9493e959d5c266412700fb658b232b20f`，已独立覆盖安装成功。仅新增分类、HTTP状态及时间，不含Key或事项正文。脚本使用43150 API、43151故障转发、43152 Android CDP；只连接独立regression包。

第三轮等待时，系统再次对本包`REMINDER_TICK`记录`Reason=frozen`（设备时间07:26:56）；用户选择自行允许独立测试包后台运行／自启动。已停止本轮自有Node测试、43150隔离API及43151／43152 ADB转发，避免抢占系统设置页面。第三轮记录只包含前两组通过，不是最终PASS。等待用户明确回复“已允许后台运行并返回测试包”后重新验收；不得假设该选择已经完成。停止时未强行打开应用清理，下一次进入测试包先`reminder_clear`清理上轮的本地测试授权，再重新配置合成环境。

Android系统Alarm诊断出现过`Reason=frozen`；须按真实设备结果判断厂商后台冻结边界，不擅自修改系统保护、不把前台通知当作关闭应用后准点通过。若手机进入其他应用或锁屏，停止输入并交回用户。

## 剩余发布门槛

1. 完成Android最新包的实际后台投递、完成后撤回、断网不发旧状态、关闭及重开；必要时增加安全的分类诊断，不读取原始Key或其他应用通知。
2. Windows最新撤回逻辑虽已通过编译及Rust定向测试，原生最小授权请求与业务条件的完整联测仍待补齐；现有系统唤醒探针只证明OS能力。正式账号无需切换，优先独立合成环境。
3. 复核在线刷新并发、原生请求总时限、异常及通知点击跳转；不要削弱失败断言来追求通过。
4. 独立MySQL V26迁移、旧数据开始证据回填、V27复盘扩展、47表整库恢复及实际旧镜像读取兼容已在第四阶段合并通过，见[复盘阶段记录](reflection-progress.md)。真实生产快照及发布切换仍须另行执行。
5. 统一正式候选版本、同身份签名打包、正式覆盖前重新确认用户已保存关闭，完成本阶段定向升级后再发布。当前未授权自动切生产或发布未验收原生包。

## 续做命令

```powershell
cd E:\Ai\假期计划\plan-platform
$env:JAVA_HOME='E:\biancheng\JDK21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn -f backend/pom.xml '-Dplan.build.directory=target-reminders' '-Dtest=NativeReminderIntegrationTest,PersonalDataIntegrationTest,DailyExperienceIntegrationTest,ReminderPollLimiterTest' package
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action test -NativeTestFilter reminders::tests
# USB已授权、用户允许独立包安装并已核对APK哈希后：
.desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/android-reminders-acceptance.mjs
```

自动续做保持关闭。额度不足时保存并通知用户，不设置自动任务，不使用重置券。
