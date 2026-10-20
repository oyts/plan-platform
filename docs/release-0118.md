# 提醒与日周复盘 0.1.18 发布准备

2026年10月20日，0.1.18／Android构建23已完成候选构建和签名核验，尚未正式发布。线上Web/API、Windows下载与Android受限源仍为0.1.17／22。没有覆盖正式客户端、切换生产入口或开放官网APK。

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

## 剩余发布门槛

1. 归档已解压且镜像已构建；按当前实际0.1.17运行配置准备0.1.18受限环境及叠加文件。不可直接运行硬编码旧版本的激活脚本。
2. 真实生产新快照在隔离库演练V25→V27、核对原业务数据及实际旧镜像读取兼容，保留快照与可执行回退；不覆盖或清空真实库。
3. 最终候选正式Windows／Android同身份保留数据覆盖前，再确认用户已保存关闭及手机可用。原登录、计划、队列、提醒默认值与复盘入口分别记录，不能用独立包替代。
4. 有足够额度完成验证及回退时才切生产；保留AI／SMTP／治理／运维／时区及私有更新挂载，失败恢复已验证入口，不回灌数据库。
5. HTTPS新增定向检查、Windows官网／可选源、Android登录受限源与真实更新检查通过后，再创建不可变标签及Windows-only GitHub附件。公开注册、官网APK／二维码保持关闭，自动下载默认关闭。

精确路径和状态见`release-0118-resume.json`。自动续做关闭；维护者最新要求额度剩余不超过10%时保存并通知，不再自动排期。
