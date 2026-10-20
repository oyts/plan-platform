# 提醒与日周复盘 0.1.18 发布记录

2026年10月21日，正式Web/API、Windows官网／可选更新源及Android登录受限源均已升级到0.1.18／Android构建23。正式双端同身份覆盖并保留原登录及空队列，新入口定向检查通过。系统通知和自动下载默认关闭，官网APK／二维码及公开注册继续关闭。

## 已完成验收

本版整合系统提醒和日周复盘，两阶段共享功能及原生定向结果见[复盘记录](reflection-progress.md)。本轮仅补剩余门槛，不重跑无关旧业务。

新提醒查询在MySQL8.4隔离V27克隆库通过：UTC与北京时间下的逻辑时间、完成／暂停过滤、重复投递抑制、完成后撤回及用户隔离正常，47张表恢复校验一致。源合成库校验及生产健康不变。报告为`reminder-query-candidates/20261020T100823Z-be55c78a/report.json`，容器已停止，库和备份保留。该报告JAR为`a2bd2d07…`；0.1.18 JAR的335个业务class逐项哈希与它一致，但版本化最终目录的真实生产快照演练仍须执行。

Windows独立包6组联测通过：默认关闭、本人确认后的UI开启、关闭测试窗口、系统任务实际唤醒并读取真实API、任务完成后下一次系统唤醒撤回、断网关闭及测试任务清理。首次通知被系统接受且历史数为1，完成后为0。证据在`acceptance-results/reflection/pc/2026-10-20T10-11-37-811Z/windows-reminder-report.json`。不声称看到横幅、锁屏准点、通知点击跳转或正式升级保留；原生Rust5项另行通过。

版本化0.1.18 API后端24项通过（复盘9、复盘AI6、提醒9），前端41项通过。浏览器20个业务断言均通过，但退出清理挂起，核对后只结束本轮测试进程树，原日志保留，不能称命令自然退出0。随后使用已通过24项测试的精确JAR和`-SkipTests -VerifiedApiSha256`重新打包成功，没有重跑或冒充额外测试。证据为`acceptance-results/release-0118/cloud-build.log`与`cloud-package-reuse.log`。

## 签名候选和暂存

本机候选目录：`production-artifacts/client-release/0.1.18/20261020T101604Z-e2ed7fab/`。

- Windows：`windows/Plan Cabin_0.1.18_x64-setup.exe`，SHA256 `ca592107af958deed4316619d10dc8c5e7913f6f475e3dccb864358e70e5f918`。沿用长期更新签名，不新增Authenticode证书。
- Android：`android/PlanCabin-0.1.18-release.apk`，SHA256 `82adbb9dfc58caeeccbca3a45e07f2b69dee2a4bea91a278141db4304e526699`。同包名`cn.zijun701.plan.app`、同长期证书、构建23、非debug、禁止明文网络。首轮依赖下载403保留失败记录，使用已有官方Maven直连选项单独重试成功，不改依赖或TLS校验。
- `metadata/candidate.json`原签名、证书、版本和哈希核验成功；状态仍为LOCAL_SIGNED_CANDIDATE，不作为已安装验收或可公开下载的证明。
- 云端归档`production-artifacts/plan-platform-0.1.18.tar.gz`，SHA256 `b7654985d3349c6d220881e350883f1138eabbc1cc91cb06426e5514f5ac18fb`；API SHA256 `0e4f00684fb04468b75fe8cc6577fe971d330225a8574d09bea0f8ddb1fd4ce5`。
- 归档已上传`/home/oyts/plan-platform/releases/0.1.18-b7654985/release.tar.gz`并核对哈希。116个归档成员均为允许目录内的普通文件／目录，无路径越界或符号链接；该目录随后按下方流程正式激活。API镜像`270968eace02…`，Web镜像`233897aaabae…`；旧镜像与服务保留。激活／演练工具及下方运维脚本修复另外受管部署，业务镜像和正式客户端字节不变。

## 本轮生产与安装结果

最终目录真实快照演练`release-drills/roadmap-20261020222301-5f707ee6/report.json`通过：同一校验和验证的生产快照恢复到两个独立库，39张表逐项一致；其中一库V25→V27迁移成功，38张原业务表的原有字段校验不变，V26执行历史保护回填和V27默认值核对通过，实际0.1.17旧镜像在迁移后的库上启动成功。原表新增列采用迁移前列投影比较，不把列布局变化误判为数据变化。容器停止，恢复库与参考库均保留。

首轮旧恢复工具与持续写入的生产`operations_alerts`表比较失败，证据保留在`roadmap-20261020222046-659636d3`。新工具改为比较同一不可变快照的两次完整恢复，不排除表或忽略校验。该比较不声称恢复后的数据等于稍后仍在变化的生产库。

激活使用`activate-reflection-release.py`，既有激活守卫9项、新版本守卫5项、快照工具4项通过，服务器预检通过后才切换。恢复点`releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261020-222712.sql.gz`已上传私有OSS并独立下载校验。演练快照为`plan_platform-mysql-20261020-222303.sql.gz`，二者分开记录。AI、Secure Cookie、SMTP、邮箱验证、成员治理、运维告警和时区及受限更新挂载保留；旧目录／镜像／旧Node／数据库与备份未删除。

正式HTTPS定向8组通过（桌面与手机网页各4组）：三模式日周事实／分页与默认设置、本机复盘草稿重开和布局、提醒界面与默认关闭、只预览不调用AI／权限／公开APK限制。证据`acceptance-results/public-web/20261020-222858-316330/report.json`。首轮测试错误地用ARRANGED查询路线，接口按设计返回400；工具改为ACTUAL后重测通过，失败记录保留，无产品代码更改。没有提交云端复盘或真实任务，没有付费AI调用或主动测试邮件。

Windows正式安装由维护者本轮保存关闭后授权，原目录`E:\PlannedCabin\Plan Cabin`使用`/UPDATE`覆盖。安装前后524个原数据文件逐文件哈希一致，凭据未导出／修改；安装器及原长期更新签名核验通过。实际原账号仍登录，初次队列0/0，日周复盘事实／编辑入口、系统提醒默认关闭、0.1.18版本及真实HTTPS连接成功，正常关闭重开后原账号仍登录。后续队列点击两次遇到电脑工具`coordinate input geometry is unavailable`，停止自动操作，由维护者本人确认重开后仍0/0且无异常；发布后本人确认检查更新为最新、自动下载未勾选。自动观察与人工补验分开记录，不冒充全部自动点击通过。尚未声称非空队列升级保留或本轮应用内跨版本安装。

Android正式0.1.17／22原登录和0/0队列已记录，维护者授权后同包名、同证书`adb install --no-streaming -r`返回Success，实际0.1.18／23安装APK哈希等于候选。只读4组通过：原登录及精确APK／队列、复盘事实和编辑入口、提醒默认关闭且未改变权限、关闭重开后的原登录及队列。证据`acceptance-results/native-reflection-0118/after-20261020T224016552147Z.json`；此前不在前台的首次尝试安全停止，无业务写入。

Windows公网5组通过，重新下载的两条EXE地址签名／哈希一致、旧安装包不变、清单no-store与下载immutable、桌面及手机官网下载、公共APK／二维码和注册限制正常。证据`acceptance-results/windows-public-cloud/2026-10-20T22-40-14-032Z-8372ace8/report.json`。首轮工具未指定已有浏览器目录，3组HTTP检查通过后缺浏览器失败；指定项目已有运行时再执行5组全部通过，失败记录保留，没有下载新依赖。

Android受限源4组通过：匿名／无效凭据拒绝、公共渠道关闭、两个既有受邀普通账号的账号绑定清单及完整APK哈希校验。证据`acceptance-results/invited-android/2026-10-20T22-41-05-579Z-e0bcc192/report.json`。APK不作为GitHub公共附件；本版实际应用内跨版本下载／安装不重复声称已测。

## 夜间OSS告警修复

原夜间任务北京时间03:15运行，本地备份成功后连续报`ossutil: command not found`。OSS工具位于root持有的`/usr/local/bin/ossutil`，cron最小PATH不包含该目录，因而新快照缺少上传和对应下载校验证据，触发OSS_FAILED与OSS_UNVERIFIED；白天人工成功核对后事件显示已恢复。故障是真实上传失败，不是数据库备份损坏，也不应关闭告警掩盖。

定时入口、上传工具和采集模板改用固定系统PATH，Python采集也通过固定路径查找OSS工具。真实校验成功后只清除OSS失败结果并持久保存，失败同样持久记录，防止普通采集读回旧失败标记。后端与客户端未变，不需要重新签名或重启业务服务。

采集器6项、运行模板5项通过。用`PATH=/usr/bin:/bin`真实执行完整定时入口，新备份`plan_platform-mysql-20261020-223940.sql.gz`上传、独立下载及哈希一致；再次普通采集后`failures=[]`、OSS证明仍匹配最新备份。03:15每日备份与5分钟采集条目保持原样，未建立Codex自动任务。修复前脚本及受限日志保留在`maintenance/oss-cron-20261021`，运行模板回退副本在`operations-runtime-20261020T223904727750Z`。历史告警事件保留，不自动删除或代管理员确认；下一次自然夜间任务尚未发生，不提前声称已观察通过。

## 版本归档与回退

Android发布后原生检查更新通过，显示当前最新、自动下载未勾选，原登录与0/0队列不变，证据`acceptance-results/native-reflection-0118/update-check-20261020T224233583832Z.json`。它是本轮已安装版本的源检查，不是再做了一次跨版本安装。

已发布[GitHub v0.1.18](https://github.com/oyts/plan-platform/releases/tag/v0.1.18)，不可变标签指向`0af761f2a97a28a4c7680c9e37cfb7e1d7fc94a3`。仅公开Windows EXE、原更新签名及SHA256SUMS，附件摘要和大小逐项核验。APK、私密验收资料、数据库和签名备份不上传。后续状态文档提交不移动标签，不改变已验收业务源码。

Windows静态入口回退：`sh /home/oyts/plan-platform/client-public/releases/windows-0118-FG6Qh2/publish.sh rollback windows-0118-FG6Qh2`。Android受限源回退：`sh /home/oyts/plan-platform/android-private/staging/android-23-df23ed2f/rollback.sh`。两者只恢复清单／下载入口，不降级已安装应用、不改变数据库。

应用回退须先执行新版`check-governance-rollback.py --target-supports-generation yes`，再通过新版`production-compose.sh`启动保留的`0.1.17-cd635586`目录，核对HTTPS后原子恢复current；不回灌旧库。切换失败时激活脚本会执行这一守卫流程。

精确路径和状态见`release-0118-resume.json`。自动续做关闭；维护者最新要求额度剩余不超过10%时保存并通知，不再自动排期。
