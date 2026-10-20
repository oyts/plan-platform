# 复盘阶段续做记录

更新时间：2026年10月20日。第四阶段共享功能、Web与独立MySQL验收已完成，PC等待用户登录独立包后继续。正式Web、Windows、Android仍为0.1.17／22；没有切换生产、覆盖正式客户端或发布APK。

## 完成范围

实现三模式日周事实、分页事项、自述、私有本机草稿、版本冲突比较、一次轻量引导、日周提醒合并、只读AI预览与会话、两周门槛及14天冷却、另开已有对象调整和私有备份格式3。默认20次AI及管理员热配置保持不变。产品规则和接口见[日周复盘](reflection-workspace.md)。

## 已验证

- 最终候选后端66项通过：Reflection9、ReflectionAI6、Reminder9、PersonalData10、DailyExperience7、AIAdjustment14、AIConversation11。JAR为`backend/target-reflection/plan-platform-api.jar`，SHA256 `c52710649db03a2e163e66f4f86f479455dc0927e28cedc3ba024731f93e4167`。证据在`acceptance-results/reflection/backend-final.log`与对应surefire报告。
- 复盘及直接受影响前端41项通过，见`acceptance-results/reflection/frontend-final.log`；Android构建附带原提醒受影响集合44项通过，二者有重叠，不相加声称85个独立用例。
- 网页10个场景乘桌面／手机视口共20项通过，见`acceptance-results/reflection/browser-final/`及同名日志。复盘交互使用模拟API，数据中心使用本机真实候选API；真实权限、409与AI任务去重另由后端测试验证。已查看320px实际截图。
- 修复初始读取与快速输入竞态；离线草稿重开、读取冲突云端后保留本机输入通过。早期失败报告保留，数据中心桌面“更多”选择器按当前真实导航修正，没有降低断言。
- Windows提醒路径及任务XML等5项Rust测试通过，见`acceptance-results/reflection/rust-final.log`。
- Android最终独立debug候选已构建，路径`acceptance-results/reflection/android-final/PlanCabin-0.1.17-debug.apk`，SHA256 `3de5fa42bb956361cfccda9d01b25e720ca5fbf6d0a8b4043559fc776d3378d5`；5项提醒路径Java测试通过。日期校验不依赖API26的java.time，兼容现有minSdk24。证据在`acceptance-results/reflection/android-final.log`，初版包保留但不用于后续验收。没有枚举设备、安装或实机测试，不是正式发布包。
- MySQL8.4独立V25→V27迁移、原进度投影不变、稳定开始证据回填、日周权限及409、格式3只读副本恢复／防重、47表整库恢复校验和实际旧0.1.17镜像只读兼容全部通过。证据为远端`reflection-candidates/20261020T061810Z-3c32df69/report.json`。合成库、恢复库、旧容器和备份保留，测试容器均停止；正式0.1.17健康和注册关闭不变。

## 正在执行和待办

1. MySQL门槛已通过。首轮脚本恢复操作键格式错误被400拒绝；第二轮复制JAR时权限过窄，容器无法读取非敏感应用包。这两轮失败证据保留；第三轮使用正确UUID与私有目录内可读JAR完成全部检查。Key和数据库环境文件权限未放宽，未删除失败库，不用前两轮替代最终PASS。
2. 独立PC包已打开，合成服务在43153、故障转发43154；等待用户在独立包自行登录。运行目录为`acceptance-results/reflection/pc/2026-10-20T06-05-12-982Z`，持久合成库与登录文件留在本机，不上传GitHub。正式账号和客户端未改变。
3. 维护者明确仅延期Android真机，与上一提醒阶段一起验收；Vivo frozen背景限制仍未通过，不宣称准点投递。上一阶段Windows真实系统唤醒探针不等于本阶段完整业务联测。
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
```

浏览器专用配置为`web/playwright.reflection.config.ts`，显式使用target-reflection JAR；Windows只通过电脑操作技能验收，认证由用户自行完成。自动任务保持关闭，剩余额度不超过5%时保存并通知。
