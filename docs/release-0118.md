# 提醒与日周复盘 0.1.18 发布准备

2026年10月21日，正式Web/API已启用0.1.18，Windows已在原目录保留数据升级到0.1.18。Windows官网／更新源和Android受限源仍为0.1.17／22；Android0.1.18／23签名候选尚未正式安装验收，不进入更新源。官网APK及公开注册继续关闭。

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
- 归档已上传`/home/oyts/plan-platform/releases/0.1.18-b7654985/release.tar.gz`并核对哈希。116个归档成员均为允许目录内的普通文件／目录，无路径越界或符号链接。已解压并核对API哈希，仅构建新标签镜像，未写`.env`、启动容器或激活。API镜像`270968eace02…`，Web镜像`233897aaabae…`；旧镜像与服务保留。

## 本轮生产与安装结果

最终目录真实快照演练`release-drills/roadmap-20261020222301-5f707ee6/report.json`通过：同一校验和验证的生产快照恢复到两个独立库，39张表逐项一致；其中一库V25→V27迁移成功，38张原业务表的原有字段校验不变，V26执行历史保护回填和V27默认值核对通过，实际0.1.17旧镜像在迁移后的库上启动成功。原表新增列采用迁移前列投影比较，不把列布局变化误判为数据变化。容器停止，恢复库与参考库均保留。

首轮旧恢复工具与持续写入的生产`operations_alerts`表比较失败，证据保留在`roadmap-20261020222046-659636d3`。新工具改为比较同一不可变快照的两次完整恢复，不排除表或忽略校验。该比较不声称恢复后的数据等于稍后仍在变化的生产库。

激活使用`activate-reflection-release.py`，既有激活守卫9项、新版本守卫5项、快照工具4项通过，服务器预检通过后才切换。恢复点`releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261020-222712.sql.gz`已上传私有OSS并独立下载校验。演练快照为`plan_platform-mysql-20261020-222303.sql.gz`，二者分开记录。AI、Secure Cookie、SMTP、邮箱验证、成员治理、运维告警和时区及受限更新挂载保留；旧目录／镜像／旧Node／数据库与备份未删除。

正式HTTPS定向8组通过（桌面与手机网页各4组）：三模式日周事实／分页与默认设置、本机复盘草稿重开和布局、提醒界面与默认关闭、只预览不调用AI／权限／公开APK限制。证据`acceptance-results/public-web/20261020-222858-316330/report.json`。首轮测试错误地用ARRANGED查询路线，接口按设计返回400；工具改为ACTUAL后重测通过，失败记录保留，无产品代码更改。没有提交云端复盘或真实任务，没有付费AI调用或主动测试邮件。

Windows正式安装由维护者本轮保存关闭后授权，原目录`E:\PlannedCabin\Plan Cabin`使用`/UPDATE`覆盖。安装前后524个原数据文件逐文件哈希一致，凭据未导出／修改；安装器及原长期更新签名核验通过。实际原账号仍登录，初次队列0/0，日周复盘事实／编辑入口、系统提醒默认关闭、0.1.18版本及真实HTTPS连接成功，正常关闭重开后原账号仍登录。后续队列点击两次遇到电脑工具`coordinate input geometry is unavailable`，停止自动操作，等待维护者只读确认。尚未声称非空队列升级保留或本轮应用内跨版本安装。

Android正式验收工具`android-reflection-formal.py`及4项保护检查已准备；未检测到授权连接设备，尚未运行正式升级。之前独立包的真机业务／后台提醒结果不替代正式APK安装和数据保留验收。

## 剩余发布门槛

1. Windows补确认重开后0/0队列，完成官网／可选更新源发布与HTTPS签名／哈希验证、原生检查更新。工具无法点击时由维护者本人补看，不绕过。
2. 维护者连接并解锁手机，记录正式0.1.17／22原登录和队列，同身份覆盖0.1.18／23并确认系统安装。只读检查新入口、默认关闭及重开。
3. Android通过后发布登录受限源并核对匿名拒绝、受邀账号绑定、完整APK哈希及原生检查更新；不开放公共APK或二维码。
4. 最后提交推送、创建不可变标签和Windows-only GitHub附件。源码可先同步，但不把未完成的Android门槛写成三端已发布。

应用回退须先执行新版`check-governance-rollback.py --target-supports-generation yes`，再通过新版`production-compose.sh`启动保留的`0.1.17-cd635586`目录，核对HTTPS后原子恢复current；不回灌旧库。切换失败时激活脚本会执行这一守卫流程。

精确路径和状态见`release-0118-resume.json`。自动续做关闭；维护者最新要求额度剩余不超过10%时保存并通知，不再自动排期。
