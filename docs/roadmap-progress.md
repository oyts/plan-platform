# 六阶段开发续做记录

更新时间：2026年10月21日。正式Web/API、Windows官网／可选源及Android登录受限源均为0.1.18／23。正式双端已保留数据覆盖，新入口和重开正常；Windows最后队列与源检查由维护者补验，Android4组自动检查通过。最终V25→V27真实快照演练、HTTPS8组、Windows公网5组及Android受限源4组通过。夜间OSS路径与持久失败标记修复后，用最小环境完整备份往返验证成功。准确状态见[0.1.18记录](release-0118.md)与`release-0118-resume.json`。下方为历史检查点；公开APK与Codex自动任务保持关闭。

**历史交接：上一轮六阶段业务及0.1.15三端发布已完成。** 独立PC九组、Android十一组、网页8项及正式升级门槛的原始结果见 [0.1.15记录](release-0115.md)，不重复无关回归。0.1.16随后启用经确认的运维／治理和管理员去重告警，真实清理仍不执行。Windows本版有一次切换期502后的人工重登录，不将后续重开成功冒充原会话跨升级保留。

## 当前阶段

下一轮第二阶段AI工作台已完成共享源码、独立双端、正式双端保留数据升级、MySQL演练及更新源发布，见[发布记录](release-0117.md)。系统导入选择器和非空队列升级等未覆盖边界仍保留，不扩大为全面回归。以下继续保留上一轮六阶段当时的历史检查点。

阶段一功能代码与共享源码定向验收已完成，尚未发布。包括私有反馈与管理员处理、手动本机草稿、诊断白名单预览和确认、持久提交防重及随机请求关联编号。

验收：后端 7 项、前端模型与直接受影响连接层 19 项、浏览器桌面／手机视口共 8 项、Windows Rust 真实回环 HTTP 连接桥 1 项、Android 连接规则单元 6 项通过；Web 和独立 Windows 回归包编译通过。已查看桌面、390px 及 320px 页面截图。浏览器初轮管理员夹具在刷新时没有保留模拟角色，后续又修正选择器标签，失败证据保留；最终八项全通过，不用旧结果替代。

浏览器最终证据在 `acceptance-results/roadmap-feedback-accepted/`，后端在 `backend/target-acceptance/surefire-reports/`，Android 单元在 `%TEMP%/plan-cabin-feedback-android/app/test-results/testDebugUnitTest/`。均为合成数据，不上传 GitHub。没有付费 AI 或真实邮件。

MySQL 新迁移演练已完成，见下方记录；独立 PC 界面候选验收仍待执行。现有 Windows 回归包已落后于最后源码，不能直接作为最终 UI 包发布；集中候选节点须重新构建。Android 真机等待用户明早连接并授权，不能用手机视口截图或连接规则单元替代。正式覆盖安装、云端切换仍待另行确认。

阶段二已完成私有快照格式、字段白名单、只读预检、原子恢复与关系重映射、只读周复盘副本、三模式 CSV、共享入口和导出大小保护。本地后端 9 项、前端直接相关 19 项通过；浏览器最终 9 项通过、桌面不适用的手机菜单用例 1 项按设计跳过，另有反馈勾选框直接受影响的 2 项复验通过。证据在 `acceptance-results/roadmap-personal-data-final/`、`acceptance-results/roadmap-personal-data-verified/` 和后端验收报告。修复了桌面勾选框被全局输入框样式撑宽造成的横向溢出，已检查真实桌面和窄屏截图。新迁移 V21 未上生产。

独立 Windows 和 Android 调试包构建通过，身份为 `cn.zijun701.plan.desktop.regression` 和 `cn.zijun701.plan.mobile.regression`，没有安装或操作真机。源资源指纹 `a9372a83262ed5f5ae3a011efde01e4905da1672b93f6a6b812ece69b7d20417`；构建清单在 `.desktop-tools/native-regression/build.json`。后端最后增加读取时的字节保护后重新构建，因此该清单的 API hash 已过期，下一次必须重新绑定最新 API 构建并验证，不能声称原生 UI 已通过。

阶段三已实现并完成共享源码及 MySQL 定向验收，尚未发布。阶段三详见 `orchestration-and-history.md`：后端 8 项、直接受影响数据中心 9 项、前端 24 项、桌面／手机视口 8 项通过。修复并发重放的旧事务快照以及 Vue 代理写入 IndexedDB 的实际问题。证据在 `acceptance-results/roadmap-organize-final/`。独立 PC 界面与 Android 真机仍待候选节点验收。

阶段四已实现并完成共享源码及 MySQL 只读验收，详见 `offline-experience.md`。后端 5 项、前端 53 项、桌面／手机视口 12 项通过，预加载防假成功保护另定向补验 2 项通过。版本仍是候选源码 0.1.14，无正式发布。独立目录 `/home/oyts/plan-platform/roadmap-candidates/20261018-1110-offline/report.json` 为 PASS：本人可编辑快照白名单、延期查询、私有备份读取、全部合成表校验和与正式健康／注册关闭不变。JAR 为 `9d6bccafcd1b04ea27a12dbab37ed9ae4e087a11604956deb371bdbeaf091a28`。测试容器已停止并保留。

阶段五主体实现、共享源码及独立 MySQL 门槛通过。详见 `operations-and-governance.md`：有界宿主机元数据采集、未知状态、站内故障去重、清理预览与数字规则审计；成员停用／恢复、持久会话代次、旧令牌持续拒绝、原因与版本审计。运维与治理默认关闭，没有删除接口、真实告警邮件或生产开关变更。后端 12 项、前端 18 项、采集器 4 项、桌面／手机网页视口 8 项通过，失败初轮保留。截图已检查，桌面导航拥挤列入第六阶段改进。证据 `acceptance-results/roadmap-governance-final/`；V23 候选 JAR `cc65bd68860ceb88d813e4178b0b1d4a6b33da49e307543b1f7dc92732e8b148`，远端目录 `/home/oyts/plan-platform/roadmap-candidates/20261018-1130-governance/report.json` 为 PASS。V1—V23、37 表合成备份恢复、治理前实际旧镜像只读兼容、停用恢复后的访问与刷新令牌拒绝、回退守卫、治理后再次整库恢复及全表校验均通过。所有候选容器已停止，数据库／镜像／备份保留，正式健康与注册关闭不变。原生 UI、Android 真机和正式发布未完成。

不要对第五阶段目录运行通用 `--verify-final-jar`／`--resume-rollback`：治理后原库和治理前恢复库按设计不同，旧夹具令牌也没有新会话代次。需要后续补验时建立明确的治理后续做路径，不能重置代次或重新运行整轮覆盖证据。

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

上一个 300 分钟窗口在 96% 时保存并安排 10:29 续做。本次已完成阶段三至五共享源码及独立 MySQL 门槛，最近边界读取已用 93%，不再开启第六阶段大步骤。实际重置为 2026-10-18 07:29:22 UTC（北京时间 15:29:22）；同一自动化安排北京时间 15:36:22 续做。未购买额度、未使用重置券、未创建重复自动化。下一窗口先读本记录，再推进第六阶段：帮助与状态、合成演示、求职设计材料、实测轻量基线、可复现部署及独立原生候选准备。修复已观察到的桌面导航拥挤，不扩展无关重设计。不要重跑已通过的一至五阶段无关验收。

## 第六阶段与候选交接

15:36 续做实查新 300 分钟窗口仅用 1%。已完成共享帮助与版本页、无需账号的本地合成演示、三端共用分组菜单、桌面常用顶栏、跳到主要内容、纯本地演示隔离与重置。保留现有蓝白视觉，不重做无关页面。截图检查发现窄桌面账号文字挤成多行，已修正为可访问完整名称的单行省略；320px 页面与更多菜单已看真实截图。

前端模型与工作区 11 项、新增帮助／演示浏览器 10 项、直接受影响的导航滚动与焦点 6 项、签名守卫 7 项、既有叠加守卫 4 项通过。新增浏览器首批 8 项与导航 6 项同跑通过；最终资源再跑 10 项，包含新增的未登录两项，不重复计成 24 个独立场景。另补原生最小宽度 800px／940px／1280px 的一项桌面导航检查通过，对应手机项目一项按设计跳过，真实截图已检查。Web/API 构建、三端版本一致、原签名包静态校验、显式归档无凭据检查和测试范围选择检查通过。新增无业务后端修改，不重跑全量后端。

第六阶段修正架构中把小程序、Redis、附件及三方合并当作已实现的过期描述，补充主要 ER 关系、核心接口、事务／409／防重／AI 未知状态解释、3—5 分钟合成演示与求职讲解。文档为 `architecture.md`、`core-api.md`、`project-showcase.md`、`performance-baseline.md`、`stable-candidate.md`。

最终 API JAR `6a68b763d7b05c48aaaae379eb7ba9eb313840ab28e79b4b3333039823690013` 已在独立 MySQL 执行有界基线与只读验证：90 次测量请求无失败，37 张合成表校验和、正式健康与注册关闭不变。远端 `roadmap-candidates/20261018-1555-final/report.json` 为 PASS，测试容器停止并保留。数据仅两名用户、少量记录；不宣称公网承载能力。早先打包前 JAR 的基线不是最终结果。

部署归档 `production-artifacts/plan-platform-0.1.15.tar.gz` 仅构建，没有上传切换；包括运维采集器、回退守卫及未启用模板。签名候选位于 `production-artifacts/client-release/0.1.15/20261018T075011Z-49f03c07/`，Windows 原更新签名与 Android 原包名／证书／受限更新源已静态核验，实际哈希见 `stable-candidate.md`。没有正式安装、公开下载变更或 GitHub Release。

独立原生包清单已刷新：源码指纹 `777cbeaf8c304e9ea61c7967fea879f94ee18162ea5a3d203a6a14ca74961225`、最终 API hash、EXE hash 均实查一致，取代上方旧清单。真实 Windows 独立 EXE 已从未登录页进入帮助，显示资源与安装版本 0.1.15，切换三种合成演示、勾选演示任务、预览并仅应用固定示例。未代填认证窗口，未进入真实账号；这不等于前五阶段登录后业务或正式升级数据保留通过。Android 没有枚举设备或安装，真机待用户明确连接与授权。

下一次继续只处理剩余门槛：用户自行登录独立合成 PC 账号后的新页面定向验收；用户保存关闭后正式 PC 同身份升级与数据／队列保留；Android 连接后新增功能与同签名升级；经确认再做当时真实生产备份、迁移／回退与 HTTPS 新功能验收，随后发布下载／受限源。运维采集权限、成员立即封禁策略、真实告警收件人与清理仍需确认，开关保持关闭。不要扩大到新功能、1.0、公开 APK 或公众注册。

本窗口最近额度实查使用 37%，不是额度耗尽而停止；自主范围已完成，剩余为用户登录、安装、设备与发布决策。按原授权暂停同一自动化，等待人工继续，不新建定时任务或为了用完额度扩展功能。独立 PC 窗口已正常关闭，正式应用未改动；没有后台候选容器或本轮本机测试服务残留。

分组菜单使用既有必更保护，直接受影响的可选／必更、默认关闭自动下载及签名失败拒装策略另定向复验 13 项通过；不是一次新的跨版本安装验收。公网教学／导航验收工具已兼容旧顶栏与新分组菜单，语法检查通过，本轮未用它们操作正式账号。

## Windows 六阶段定向验收

2026 年 10 月 18 日，用户自行登录独立 `plan-regression.exe` 0.1.15 后，用 Windows 界面自动化完成九组新增业务检查。EXE `6b0bd3358efdee4598e41f631671f4f28c505dc22c9375820487040914ee15cc`、最终 API `6a68b763` 和共享源码 `777cbeaf` 实查一致。界面通过真实 Rust 桥连接本机隔离 API，未使用 WebView 调试接口代替 Windows 操作，未操作正式客户端或真实账号。

- 反馈：诊断白名单、草稿保存与关闭重开、发送预览及确认后提交；使用合成文字。
- 数据中心：实际 Windows 文件选择器读取用户明确批准的合成 JSON，只读预检后恢复为新副本；同一文件重复确认仍为同一批次、共一批。
- 编排：三项任务的周期结构复制经过差异预览与确认；长路线服务端第一页十步、第二页两步，路线暂停／恢复后步骤保留；待办历史能查到恢复样本。
- 离线：真实 TCP 中断下保存周复盘草稿，退出独立客户端并重开后恢复，云端保存保持禁用。另一轮离线待办编辑加独立新增共两项，模拟另一会话修改后真实409停靠；共同基础、本机和最新云端内容均正确。人工选择本机标题、云端备注，确认生成新操作并同步后，两项均完成、队列0／冲突0；只读 API 复核后续事项恰好一份。
- 预加载：明确选择七天窗口，五次读取成功；断网关闭重开后仍见三项任务、星期日休息／星期六计划日，缓存清单可读，离线预加载禁用。
- 运维与治理：没有采集证据时显示未知；两次核对复用同一站内事件，无真实邮件。保留规则只有预览与规则保存入口，没有删除入口。成员状态与空审计列表只读可用，没有自动操作停用、恢复或安全配置。

这九组不是一次从头全绿的脚本运行。原始 UI 观察在 `acceptance-results/roadmap-pc/2026-10-18T09-06-41-903Z/`；中断期间原内存合成夹具停止，其数据未保留，但已完成观察保留。后续改为独立文件 H2 和控制文件，复用合成登录凭据但创建了新账号编号；用户再次自行登录后继续剩余项目，不伪称同一数据库连续验收。最终 `acceptance-results/roadmap-pc/2026-10-18T12-48-17-068Z/report.json` 为 PASS，包含110条私有界面观察索引与准确的分段说明。窗口越界／弹窗几何问题通过重新观察和滚动恢复，未改业务源码。两份目录、凭据、数据库和观察均被 Git 忽略。

工具 `roadmap-pc-fixture.mjs` 验证源码及包哈希，创建隔离合成用户并最多运行四小时；`roadmap-pc-network.mjs` 通过控制文件切换自己的 TCP 转发，不改系统网络、不依赖终端输入。工具语法检查通过，控制路径边界及三个控制命令共六项保护测试通过；这六项不计入九组业务。测试结束正常关闭独立窗口并停止自有 API／转发，43134／43137 无监听，正式应用不动。没有付费 AI、真实邮件、生产写入或文件清理。

本轮不包含正式同身份跨版本安装、非空队列跨升级保留、公网 V23 联测、Windows 导出保存目标、Windows 未来批量409及管理员回复操作。后两项已有共享源码／Android 证据，不冒充本轮 PC 覆盖。成员安全设置不通过 Windows 自动化修改；后端和 Android 已有独立证据。签名正式包、生产备份和发布确认仍是后续门槛。

仅需复现剩余问题时，在仓库根目录使用以下命令；重新运行会创建新合成数据，不恢复旧账号编号，不能拿新结果覆盖旧证据。登录由用户自行完成，界面操作遵循电脑操作规范，不自动填密码：

```powershell
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/roadmap-pc-fixture.mjs
# 用上一步返回的实际目录替换 <run-directory>，不能指向正式目录。
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/roadmap-pc-network.mjs offline <run-directory>
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/roadmap-pc-network.mjs online <run-directory>
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe scripts/roadmap-pc-network.mjs stop <run-directory>
& .desktop-tools/node22/node-v22.23.3-win-x64/node.exe --test scripts/roadmap-pc-network.test.mjs
```

## Android 六阶段定向验收

2026 年 10 月 18 日，用户明确连接并授权后，在 V2426A／Android 15 覆盖安装独立 `cn.zijun701.plan.mobile.regression` 0.1.15。APK SHA-256 为 `38c2291f4f88c388beeb8746c5a0991912c8caedbcb7479c197d5ca80be48bd9`，仍绑定最终 `6a68b763` API 和 `777cbeaf` 共享源码。正式 `cn.zijun701.plan.app` 前后均为 0.1.14／19，没有卸载、覆盖、登录切换、队列提交或清数据。

11 个业务场景通过：原生版本／Java 网络桥、反馈草稿重开和白名单诊断／同意提交／管理员回复、私有备份预检与原子恢复新副本／重复确认防重、结构复制／未来批量真实409保留草稿、路线分页／暂停恢复／待办历史、真实 TCP 断网周复盘草稿重开与人工核对、离线队列真实409三方比较／重新提交／后续操作保留、有限预加载／离线清单重开、未知运维状态／告警去重／只预览规则、合成成员停用恢复／旧令牌拒绝、移动分组菜单与三模式本地演示。检查了实际手机截图；所有修改仅为合成账号，运维和治理只在隔离进程开启，生产仍关闭。AI 关闭、邮件自动配置排除，无付费调用、真实邮件或生产写入。

证据分两段，不能写成一次全绿运行：`acceptance-results/native/2026-10-18T08-58-26-271Z-9134f788/report.json` 的前10项通过，最后帮助用例因脚本对包含状态文字的复选框采用精确名称匹配而失败；修正脚本后只补帮助，用 `2026-10-18T09-00-48-297Z-d0c9b393/report.json` 验证合成登录与帮助场景2项通过，后者包含演示前后个人记录一致性。更早的失败为隔离服务邮件健康探针、重复管理员参数、未等账号恢复便切换、提示条多匹配，均在测试工具修正，未改业务源码或重构候选包。框架防护8项通过；重复执行和登录准备不重复计为业务覆盖数。

文件恢复通过真实页面文件读取处理器注入合成 `File`，再经真实 Java 桥提交 API；这不等于系统文件选择器或保存／分享目标已验收。本轮也不等于正式同签名跨版本升级、非空队列升级保留、Windows 登录后验收或公网 V23 新功能验收通过。

可复现命令：`powershell -NoProfile -ExecutionPolicy Bypass -File scripts/native-regression.ps1 -Action run -Platform android -Roadmap`。只补帮助可加 `-RoadmapSection help`；其他阶段使用完整新增业务集合，不运行旧13项全量。脚本拒绝 Windows 模式；只覆盖独立 APK，已安装且字节相同则跳过重复安装。截图跳过密码页并核对独立应用前台；运行结束停止自有进程、移除自有调试／业务转发，保留正式应用和其他映射。没有重新启用已暂停的自动化。

后续仍需用户确认正式保存关闭与升级、PC 合成登录及生产发布窗口。手机独立验收通过后，不直接把候选包推送给朋友：先完成剩余发布门槛，再发布官网 Windows 与登录受限 Android 更新源；公开 APK／二维码和注册保持关闭。

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
