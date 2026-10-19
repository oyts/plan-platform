# AI工作台阶段二续做记录

更新时间：2026年10月19日。整阶段功能、共享源码定向测试和独立Windows有限界面验收已完成；正式三端仍0.1.16／Android21，没有切换生产、覆盖正式客户端或发布新包。Codex自动任务保持暂停。

## 当前完成

独立云端草稿V25及接口、本机按账号／服务器隔离的自动保存、版本比较、过期继续的安全校验、清空竞态保护、导出导入、对话分类、手机双面板、有界滚动、静态模式示意与确认切换、阶段分页和完整参考分钟、统一RGB／HEX选色均已实现。规则见[功能说明](ai-workbench.md)。

后端31项通过：`AiWorkbenchIntegrationTest`6项、直接受影响的`AiConversationIntegrationTest`11项和`AiAdjustmentIntegrationTest`14项。H2空库V1至V25迁移通过。前端单元20项通过，涉及工作区保存／隔离／容量／取消竞态、颜色边界、路线汇总和直接受影响AI规则。报告在`backend/target-acceptance/surefire-reports/`。

最终Web、独立Windows调试包和Android共享页面构建通过。独立Windows通过7组有限检查：默认推荐入口／草稿箱、21步完整315分钟与分页、独立副本禁止直接发送／生成、手改后本机保存、刷新后恢复且不恢复生成勾选、通过原生连接桥保存到隔离服务、HEX转RGB及三位数字显示。用户手工登录合成账号，未操作正式账号、未创建真实业务。Android未安装、未进行真机测试。

PC证据：`acceptance-results/ai-workbench/pc/2026-10-19T09-15-52-614Z/pc-ui-observations.json`。主要草稿检查使用`c260130c…`调试包；最后仅修正RGB输入内边距，重新构建`d41d89c5…`并检查原合成会话保留及三位数字可见。最终源码指纹`5d47ab45cf3a9428c3e671c36bad0ea9b38350f668f001b37c6ac2476394913a`；API哈希`55fb12c45ee51ea86f251f52338796846725740dde5b9d2673c7435d2c821fa8`。旧的全平台`build.json`不代表本阶段结果，不能拿其中旧APK作为候选。

## 集中验收中的修复

首轮手机勾选同意失败：吸底表单的focus-within在点击过程中改变位置，已改为只跟随实际视口缩小。后续手机三模式调整和确认生成通过。

颜色用例发现原标签编辑栏仍给色盘固定48像素，完整RGB／HEX控件溢出。已修复布局，并完善合成计划夹具的加载数据。失败证据保留在`acceptance-results/ai-workbench/browser-first/`、`browser-final/`和`browser-verified/`，不以重跑掩盖。

最终桌面／手机视口共34项通过、0失败、0跳过，命令退出0，列表及JSON报告保存在`acceptance-results/ai-workbench/browser-final-external-preview.log`，截图在同名目录。此前`browser-verified`的33通过／1失败及中间轮次的收尾挂起证据保留；最终改为单独管理回环预览服务，使用专用mock配置，不再为全部拦截API的用例额外启动Java。最终报告不依赖旧轮次结果。

PC截图另发现窄列中数字输入默认内边距挤住RGB三位数，已缩小控件横向内边距，并在最后34项网页用例和重建后的原生窗口确认。沿用现有白底、蓝色和网格视觉，不改变旧HEX存储或业务逻辑。

## 待完成门槛

1. Android新布局的真机软键盘、对话／草稿切换、本机重开与系统文件导入／分享尚未验证；不以手机视口或共享页面构建替代。
2. 新迁移V25的独立MySQL演练与恢复验证、正式版本编号、同身份签名、双端保留数据升级及生产发布均未执行。需进入发布节点后完成，不能重打0.1.16正式包或直接推未验收APK。
3. PC本次没有点击删除／隐私确认或使用系统文件选择器；版本冲突、批量跳过、继续草稿校验等由共享源码测试覆盖，不声称每个分支都已原生点击。
4. 本次不调用付费AI、不发真实邮件、不启动第三阶段系统通知开发。生产与原生发布另行核对确认。

## 续做命令

以下命令在本机项目目录执行，Java21、Node22和Playwright缓存沿用项目已有工具：

```powershell
mvn -f backend/pom.xml '-Dplan.build.directory=target-acceptance' '-Dtest=AiWorkbenchIntegrationTest,AiConversationIntegrationTest,AiAdjustmentIntegrationTest' test
npm --prefix web test -- src/aiWorkbench.test.ts src/colors.test.ts src/routeStages.test.ts src/aiPlanning.test.ts src/aiAdjustment.test.ts src/privateDrafts.test.ts
npm --prefix web run build
# 单独终端启动回环预览，测试结束后停止该预览；四份用例全部拦截API。
npm --prefix web run preview -- --host 127.0.0.1 --port 43138 --strictPort
# 另一个终端执行：
npm --prefix web run test:e2e -- --config playwright.ai-workbench.config.ts --output=../acceptance-results/ai-workbench/browser-final-external-preview --reporter=list,json
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/desktop.ps1 -Action regression
npm --prefix web run build:android-web
```

PC合成服务可用`node scripts/ai-workbench-pc-fixture.mjs`新建；中断后用`--resume acceptance-results/ai-workbench/pc/原运行目录`保留原数据库恢复，仅接受同一已核验API／客户端字节。工具不会代填认证。最后CSS重建改变了客户端哈希，因此不要直接把旧清单改写为新清单来绕过校验；本次最终显示复验使用仍在运行的同一隔离服务。

不要运行旧AI真实调用脚本、旧版本生产切换或全量业务回归。不要把浏览器手机视口当成Android真机，也不要把独立调试包当成正式签名包。
