# 六阶段开发续做记录

更新时间：2026 年 10 月 18 日。正式服务和客户端保持 0.1.14；本轮没有生产切换、公开注册或 Android 发布。依据 `development-roadmap.md` 按完整阶段开发，再集中验收新增及受影响功能。

## 当前阶段

阶段一功能代码与共享源码定向验收已完成，尚未发布。包括私有反馈与管理员处理、手动本机草稿、诊断白名单预览和确认、持久提交防重及随机请求关联编号。

验收：后端 7 项、前端模型与直接受影响连接层 19 项、浏览器桌面／手机视口共 8 项、Windows Rust 真实回环 HTTP 连接桥 1 项、Android 连接规则单元 6 项通过；Web 和独立 Windows 回归包编译通过。已查看桌面、390px 及 320px 页面截图。浏览器初轮管理员夹具在刷新时没有保留模拟角色，后续又修正选择器标签，失败证据保留；最终八项全通过，不用旧结果替代。

浏览器最终证据在 `acceptance-results/roadmap-feedback-accepted/`，后端在 `backend/target-acceptance/surefire-reports/`，Android 单元在 `%TEMP%/plan-cabin-feedback-android/app/test-results/testDebugUnitTest/`。均为合成数据，不上传 GitHub。没有付费 AI 或真实邮件。

MySQL 新迁移演练和独立 PC 界面候选验收仍待执行。现有 Windows 回归包是在最后两项细微界面调整前构建，不能直接作为最终 UI 包发布；集中候选节点须重新构建。Android 真机等待用户明早连接并授权，不能用手机视口截图或连接规则单元替代。正式覆盖安装、云端切换仍待另行确认。

下一步进入阶段二个人数据中心，先核对现有导出、旧迁移和实体关系，再实现版本化快照、预检和恢复为新记录。阶段三至六尚未开始实现，不要根据路线图文字或历史测试记录声明完成。

## 本机命令

在仓库根目录使用本机 JDK21 和 Maven，构建隔离验收 API：

```powershell
$env:JAVA_HOME='E:\biancheng\JDK21'
& 'E:\biancheng\maven\apache-maven-3.9.4-bin\apache-maven-3.9.4\bin\mvn.cmd' -f backend/pom.xml '-Dplan.build.directory=target-acceptance' '-Dtest=FeedbackIntegrationTest' package
```

将 `.desktop-tools/node22/node-v22.23.3-win-x64` 加入当前进程 PATH 后，在 `web` 中执行：

```powershell
npm run test -- src/feedbackModel.test.ts src/api/client.test.ts
npm run build
npm run test:e2e -- e2e/feedback.spec.ts
```

浏览器会自动启动隔离 H2 服务和预览，证据含合成账号，不进入公开仓库。正式数据库、用户队列、签名、旧发布和备份一律保留。

## 额度和继续规则

每次唤醒重查真实 300 分钟窗口。在阶段边界或约十分钟复查，90% 收拢、95% 保存并将同一个 `automation` 调到实际重置后至少六分钟。所有自主工作完成或只剩必须用户参与的门槛时暂停自动化。不要创建重复任务，不消耗重置券，也不为了用完额度扩展范围。
