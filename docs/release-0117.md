# AI规划工作台 0.1.17 发布记录

2026年10月20日，Web/API、Windows 官网下载与可选更新、Android 登录受限更新均已发布 **0.1.17／Android构建22**。正式双端同身份覆盖升级通过，原登录、计划和重开后的0/0队列保留。公开注册和官网APK／二维码继续关闭，自动下载未勾选，不强制更新。

本版交付独立云端草稿、本机未发送文字与编辑自动保存、继续编辑和导出导入入口，电脑并列对话／草稿和手机双面板，模式示意、路线完整参考投入及RGB／HEX精确配色。独立草稿不自动复活过期会话、发送请求、扣费或生成计划。以下历史验收记录保留，正式发布结果见末节。

## 本轮验收

Android15独立包使用真实Java桥和隔离H2，最终6组通过：云端保存草稿读取、21步完整315分钟与分页、实际软键盘输入与面板切换、本机进程重开恢复／不恢复生成勾选、云端保存与精确颜色、真实分享面板唤起及取消。导出后的实际缓存JSON格式、21步及修改标题核对通过；没有选择接收应用、外发文件或调用模型。当时正式0.1.16未被覆盖，随后正式升级结果见末节。

最终证据`acceptance-results/native/2026-10-19T14-35-33-922Z-76045ff2/report.json`。首轮工具tap不支持；第二轮5组通过；第三轮实际分享成功但临时状态被自动保存状态替换，工具断言失败。最终改为核对真实导出文件及返回应用，不删除失败证据、不削弱导出内容断言。系统文件导入选择器及分享至目标应用后的保存结果仍未测，不称作完整导入／分享端到端通过。

真机截图发现HEX在窄列被裁切，已按原白蓝网格设计让HEX换行，原生截图确认`#00FF80`完整可读。最终网页仅补测直接受影响的配色2项，0失败、命令退出0，证据`acceptance-results/ai-workbench/hex-wrap-browser.log`；此前工作台34项共享页面结果单独保留，不重复无关业务。0.1.17 API重新构建时31项后端通过。界面修复后重新生成独立包与正式签名候选，前一候选保留、未公开。

## MySQL真实快照演练

服务器早期演练`release-drills/roadmap-20261019142529-d8d0dce1/report.json`为PASS：真实生产库新备份恢复到`plan_restore_20261019_142530`，V24→V25迁移通过，37张原业务表全表校验不变；实际原0.1.16镜像在迁移后恢复库上启动通过。演练容器停止并保留，无公共端口、模型密钥或SMTP。正式激活前又在最终目录取新快照演练，见末节。

备份`releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261019-142529.sql.gz`保留。首轮路径守卫错误地要求备份属于current目录，已修正为仅接受受管发布的backups目录及严格文件名，未放宽到任意路径；失败目录也保留。此次证明真实快照迁移、原表不变和原镜像兼容，不声称已在MySQL上写入合成独立草稿并做含该记录的整库恢复。

## 最终候选

目录`production-artifacts/client-release/0.1.17/20261019T143117Z-1929866e/`：

- Windows EXE SHA256：`3f3f6c8ddf438b4f05f981d9589bc604059ac3d565a0a0d5a374c176cd628a0d`，原长期更新签名校验通过；未新增Authenticode证书。
- Android正式APK SHA256：`9330bf71ae6d821a8cf3f304c91308772a7483d264599e1be0e8dc603c7ed7c2`，同包名`cn.zijun701.plan.app`、同长期证书、构建22、非debug且禁止明文网络。
- 最终云端归档SHA256：`cd635586c759db28be0cd3b6747de34631bc268199f52cd08d656cfb72608d0b`。
- API SHA256：`b009c2b9035aaa01fda02b2bb692ec8185d39016afd25ab53ea238aff57332eb`。最后只有前端HEX布局修复，API字节与MySQL演练完全相同。

最终归档已上传并在`releases/0.1.17-cd635586`正式启用。早期候选`releases/0.1.17-61a7948b`含修复前Web，不得作为正式入口；旧目录与镜像保留。

## 正式生产与升级结果

最终目录演练`release-drills/roadmap-20261019221657-40ff370c/report.json`通过：真实新快照V24→V25、37张原表校验不变、实际0.1.16旧镜像兼容。保留停止的演练容器及恢复库，不覆盖真实库；不声称已验证在MySQL写入独立草稿后的整库恢复。

生产使用`activate-ai-workbench-release.py`激活，9项守卫通过；保留AI、SMTP、治理、时区、运维和邮件告警配置以及只读密钥挂载。激活前另取恢复点`releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261019-222409.sql.gz`，已上传私有OSS并独立下载哈希核对。迁移演练使用22:16:58恢复点，两者不可混称。旧0.1.16目录、镜像、旧Node、数据库及备份保留。

生产HTTPS新增6项通过：受邀合成普通账号独立草稿保存、409、他人404、私有no-store、管理权限403与匿名401，以及桌面／手机网页加载。证据`acceptance-results/public-web/20261019-222525-a3d405/report.json`。

Windows正式7组只读检查通过：原登录与计划、0/0队列、新AI工作台、0.1.17及HTTPS实际连接、自动下载关闭、正常关闭重开保留、正式源检查最新。启动前498个原应用数据文件哈希一致，没有卸载、切账号、清队列或代填认证。证据`acceptance-results/native-ai-workbench-0117/windows-formal.json`。

Android正式4组通过：实际APK哈希及原登录／0/0队列、新工作台与草稿箱、对话／草稿切换、关闭重开保留。发布受限源后另2组检查通过（包含重复身份／哈希门槛，不累加为6项独立业务）。失败轮次分别为OEM界面读取异常和测试标题未包含“02”前缀，修正工具后通过；4项工具守卫通过。证据`acceptance-results/native-ai-workbench-0117/after-20261019T223238975173Z.json`及`update-check-20261019T223525830574Z.json`。

Windows公网5项通过：健康与关闭注册、清单no-store、两条EXE URL的签名／哈希和不可变缓存、桌面／手机官网下载、Android公共入口关闭。旧安装包逐字节保留，证据`acceptance-results/windows-public-cloud/2026-10-19T22-34-52-556Z-bc8114a6/report.json`。

Android受限源4项通过：匿名及无效凭据拒绝清单和APK、公开APK与旧静态源404、两个受邀普通账号的私有清单及完整APK哈希。证据`acceptance-results/invited-android/2026-10-19T22-33-59-629Z-c96513db/report.json`。正式手机检查最新，自动下载未勾选；APK不上传GitHub公共附件。

核查旧OSS失败记录后，实际完成上传／独立下载校验再更新故障状态，并接入本轮真实恢复演练证据。管理页已显示本地备份、OSS、恢复、磁盘58.2%空闲及目录完整性正常；两条既有告警事件保留待管理员核对，没有自动删除或确认。

本版不声称已验证非空队列跨升级或应用内跨版本安装。未选择外部分享接收应用或验证系统导入选择器。本轮没有付费AI调用或主动发送测试邮件，既有管理员告警仍按生产策略运行。

## 回退与后续

应用回退先用新版`check-governance-rollback.py --target-supports-generation yes`核对，通过后使用新版`production-compose.sh`恢复保留的0.1.16目录、核对健康再切换current，不回灌旧数据库或清零代次。旧硬编码激活工具不可直接重用。

Windows静态入口回退：`sh /home/oyts/plan-platform/client-public/releases/windows-0117-c9LfHB/publish.sh rollback windows-0117-c9LfHB`。Android私有源回退：`sh /home/oyts/plan-platform/android-private/staging/android-22-d7836193/rollback.sh`。不降级已安装应用或修改数据库。

已发布 [GitHub v0.1.17](https://github.com/oyts/plan-platform/releases/tag/v0.1.17)，不可变标签指向`e57a40b515c3e83a01572af7f9bff65159cbd889`，源码已同步。公开附件仅Windows EXE、更新签名及`SHA256SUMS.txt`，逐项核对GitHub摘要与大小；APK、私密验收资料及签名备份不上传。后续状态文档提交不移动该标签或修改已验收业务源码。

下一完整阶段为系统通知与事项提醒，尚未开工。Codex自动任务保持关闭。精确状态见`release-0117-resume.json`。
