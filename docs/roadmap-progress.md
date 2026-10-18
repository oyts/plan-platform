# 六阶段开发续做记录

更新时间：2026 年 10 月 18 日。正式服务和客户端保持 0.1.14；本轮没有生产切换、公开注册或 Android 发布。依据 `development-roadmap.md` 按完整阶段开发，再集中验收新增及受影响功能。

## 当前阶段

阶段一功能代码与共享源码定向验收已完成，尚未发布。包括私有反馈与管理员处理、手动本机草稿、诊断白名单预览和确认、持久提交防重及随机请求关联编号。

验收：后端 7 项、前端模型与直接受影响连接层 19 项、浏览器桌面／手机视口共 8 项、Windows Rust 真实回环 HTTP 连接桥 1 项、Android 连接规则单元 6 项通过；Web 和独立 Windows 回归包编译通过。已查看桌面、390px 及 320px 页面截图。浏览器初轮管理员夹具在刷新时没有保留模拟角色，后续又修正选择器标签，失败证据保留；最终八项全通过，不用旧结果替代。

浏览器最终证据在 `acceptance-results/roadmap-feedback-accepted/`，后端在 `backend/target-acceptance/surefire-reports/`，Android 单元在 `%TEMP%/plan-cabin-feedback-android/app/test-results/testDebugUnitTest/`。均为合成数据，不上传 GitHub。没有付费 AI 或真实邮件。

MySQL 新迁移演练已完成，见下方记录；独立 PC 界面候选验收仍待执行。现有 Windows 回归包已落后于最后源码，不能直接作为最终 UI 包发布；集中候选节点须重新构建。Android 真机等待用户明早连接并授权，不能用手机视口截图或连接规则单元替代。正式覆盖安装、云端切换仍待另行确认。

阶段二已完成私有快照格式、字段白名单、只读预检、原子恢复与关系重映射、只读周复盘副本、三模式 CSV、共享入口和导出大小保护。本地后端 9 项、前端直接相关 19 项通过；浏览器最终 9 项通过、桌面不适用的手机菜单用例 1 项按设计跳过，另有反馈勾选框直接受影响的 2 项复验通过。证据在 `acceptance-results/roadmap-personal-data-final/`、`acceptance-results/roadmap-personal-data-verified/` 和后端验收报告。修复了桌面勾选框被全局输入框样式撑宽造成的横向溢出，已检查真实桌面和窄屏截图。新迁移 V21 未上生产。

独立 Windows 和 Android 调试包构建通过，身份为 `cn.zijun701.plan.desktop.regression` 和 `cn.zijun701.plan.mobile.regression`，没有安装或操作真机。源资源指纹 `a9372a83262ed5f5ae3a011efde01e4905da1672b93f6a6b812ece69b7d20417`；构建清单在 `.desktop-tools/native-regression/build.json`。后端最后增加读取时的字节保护后重新构建，因此该清单的 API hash 已过期，下一次必须重新绑定最新 API 构建并验证，不能声称原生 UI 已通过。

阶段三已实现并完成共享源码及 MySQL 定向验收，尚未发布。阶段三详见 `orchestration-and-history.md`：后端 8 项、直接受影响数据中心 9 项、前端 24 项、桌面／手机视口 8 项通过。修复并发重放的旧事务快照以及 Vue 代理写入 IndexedDB 的实际问题。证据在 `acceptance-results/roadmap-organize-final/`。独立 PC 界面与 Android 真机仍待候选节点验收。

阶段四已实现并完成共享源码及 MySQL 只读验收，详见 `offline-experience.md`。后端 5 项、前端 53 项、桌面／手机视口 12 项通过，预加载防假成功保护另定向补验 2 项通过。版本仍是候选源码 0.1.14，无正式发布。独立目录 `/home/oyts/plan-platform/roadmap-candidates/20261018-1110-offline/report.json` 为 PASS：本人可编辑快照白名单、延期查询、私有备份读取、全部合成表校验和与正式健康／注册关闭不变。JAR 为 `9d6bccafcd1b04ea27a12dbab37ed9ae4e087a11604956deb371bdbeaf091a28`。测试容器已停止并保留；阶段五、六待开发。

MySQL V22 的合成演练、全表 dump 恢复和实际旧 0.1.14 镜像兼容已通过，远端目录 `/home/oyts/plan-platform/roadmap-candidates/20261018-1045-orchestration`，`report.json` 为 PASS。JAR 为 `724d7fe26b91c0c8203e2693288fcaadce6f70e99818dea3b49887a0867c86f9`。没有操作真实业务库；正式站、原生包和更新源保持 0.1.14。

## MySQL 独立演练与最后补验

远端候选目录为 `/home/oyts/plan-platform/roadmap-candidates/20261018-0625-feedback-data`，仅合成数据库和账号。`report.json` 已为 PASS：V1—V21 MySQL 8.4 迁移、反馈权限／防重／管理员处理、三模式备份恢复与关系重映射、整库 dump 恢复全表校验、实际旧 0.1.14 镜像读取升级库及数据不变、公网健康和注册关闭不变。初次回退脚本误用了镜像内 JAR 路径，随后修正；第二次读取范围超过旧待办接口范围，改为同一天后通过，失败报告保留，没有反复重建迁移数据。

阶段二最终流式读取补验已在本次续做完成：JAR `857e06fd5fdfc8010dc146de4031d9ef0ba27bba18be8ba2ab4db4c33bdb1c3b` 的 MySQL 备份与 CSV 读取成功，32 张表校验不变，正式健康与注册关闭不变。以下是已执行命令，仅供复现，不要重跑作为新的工作：

```powershell
scp backend/target-acceptance/plan-platform-api.jar oyts@115.29.176.141:/home/oyts/plan-platform/roadmap-candidates/20261018-0625-feedback-data/api.jar
scp scripts/roadmap-mysql-acceptance.py oyts@115.29.176.141:/home/oyts/plan-platform/roadmap-candidates/20261018-0625-feedback-data/verify.py
ssh oyts@115.29.176.141 'python3 /home/oyts/plan-platform/roadmap-candidates/20261018-0625-feedback-data/verify.py --directory /home/oyts/plan-platform/roadmap-candidates/20261018-0625-feedback-data --verify-final-jar'
```

脚本仅在原独立候选重启新 JAR、读取合成备份和 CSV、比较数据校验和，结束后停止候选并保留资料。不要重跑没有续做参数的整轮脚本，不输出私有配置或凭据。正式服务、MySQL 业务库、旧 Node、更新源和真实账号完全保留。

## 本次额度停止点

上一个 300 分钟窗口在 96% 时保存并安排 10:29 续做。本次醒来实查已恢复，最近边界读取已用 29%，重置由实际工具结果控制。未购买额度、未使用重置券、未创建重复自动化。下一步进入阶段四，不重复已通过的一至三阶段无关验收。

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
