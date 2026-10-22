# 个人整套模板阶段续做记录

2026年10月22日最新状态：本阶段已合并模板教学与试用收口，完成三端0.1.19／Android24正式发布，见[发布记录](release-0119.md)。下方未发布／未验收文字为历史过程，不能当作当前缺口。

更新时间：2026年10月21日。开发基于正式0.1.18／Android23；生产和更新源未切换，不复用0.1.18版本号发布新原生包。

## 已完成

私人结构捕获、独立快照、分类收藏、分页搜索、全结构预览、资料编辑、显式结构更新、删除及从模板建立新计划／路线。复用原结构复制事务，补版本409、操作防重及本机结果未知保护。V28新增两表；备份格式4兼容1—3。见[使用规则](personal-template-library.md)。

最终类型化时间程序后端24项通过：模板8、嵌套校验2、数据中心10、复制3、复盘备份兼容1。日志 `acceptance-results/templates-backend-typed-time-final.log`，JAR SHA256 `d8337116cc33621e19b0d32ab4e3d4559c3a0b3ae4b02daa0b03a710f5b400ce`。前端单元11项及Web构建通过。

早期接口重名及浏览器下拉定位失败已修正；截图发现的时间偏移必须使用与正常任务一致的Hibernate类型化读写，不能只改JDBC字符串。失败证据保留，不用早期通过数替代最终程序验收。

## 待核实和续做

1. 最终网页18例全部通过，结果位于 `acceptance-results/templates-browser-accepted.json`；包括时间断言、结果未知原样重试、409保留输入、备份及未提交表单离开／重开，不自动保存为模板。最终前端单元为 `templates-frontend-final.log`，构建为 `templates-web-final-build-second.log`。此前时间断言和未等待登录恢复即再次刷新的测试失败证据保留。
2. MySQL脚本 `scripts/templates-mysql.py`：首次目录 `/home/oyts/plan-platform/template-candidates/20261020T232300Z-2e0b8539` 使用较早JAR，Asia时区复制时间断言失败。最终程序在新目录 `/home/oyts/plan-platform/template-candidates/20261020T233000Z-d8337116` 的 `report.json` 为PASS：V27→V28、46张原业务表不变、UTC及北京时间逻辑时间、旧0.1.18镜像读取、49表全库恢复校验均通过。合成数据库 `template_acceptance_d8337116`、恢复库 `template_restore_d8337116` 及备份保留，测试容器已停止，生产仍0.1.18。各时区通过正常API创建合成来源，不据此声称切换JVM时区会迁移历史时间。
3. Windows独立包与Android真机尚未验收；本轮没有覆盖正式应用。签名候选需要新统一版本，正式PC覆盖须保存关闭确认，Android系统安装和授权由本人操作。
4. 正式发布前对当时生产快照演练、核对回退守卫、签名／哈希、原账号／队列保留、HTTPS及更新源。官网APK、公众注册保持关闭。
5. 源码提交 `96109cd` 已推送；后续记录补提交。Web、desktop-web、android-web资源构建通过，但不是EXE／APK验收。补充定向离线安全用例1项通过（同文件其余8项按范围跳过），连同原11项单元合计12项；证据 `templates-no-offline-replay.log`。本轮本机测试服务已停止。额度剩余10%停止并直接通知，不开自动任务。

后端定向命令：

```powershell
$env:JAVA_HOME='E:\biancheng\JDK21'
mvn.cmd -f backend/pom.xml '-Dplan.build.directory=target-templates' '-Dtest=PersonalTemplateIntegrationTest,TemplateBlueprintTest,PersonalDataIntegrationTest,OrchestrationIntegrationTest#planCopyRemapsStructureShiftsDatesAndDropsAllExecutionHistory+routeCopyRemapsStagesAndExplicitDependenciesWithoutCompletionOrPause+copiesRejectForeignOwnerExpiredPreviewAndShortenedPeriod,ReflectionIntegrationTest#privateV4BackupRestoresReviewsAsReadonlyAndAcceptsV2' package
```

浏览器配置为 `web/playwright.templates.config.ts`。Windows托管进程回收卡住时，独立启动 `web/scripts/start-acceptance-api.mjs`（`PLAN_ACCEPTANCE_JAR=backend/target-templates/plan-platform-api.jar`）及43130预览服务，设置 `PLAN_TEMPLATE_EXTERNAL_SERVERS=1` 再运行16例。只结束本轮核实的进程树，不碰正式应用。浏览器手机视口不等于APK真机通过。
