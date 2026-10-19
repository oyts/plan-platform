# 计划舱邮件安全与运行保障发布记录

2026年10月19日，Web/API、Windows官网下载与可选更新源、Android登录受限更新源均发布为 **0.1.16／Android构建21**。本版完成下一轮第一阶段，不扩展到AI工作台开发。公众注册、官网APK／二维码保持关闭，自动下载仍需用户主动勾选，更新非强制。

## 本版交付

账号设置显示邮箱验证状态，支持主动发送、冷却和重新读取；SMTP失败、过期和已使用链接有明确反馈。已登录用户可以访问验证页，失效链接不注销已有账号。存量未验证账号仍可使用，不批量伪造验证状态。邀请、验证与密码找回邮件均不再抄送管理员。

管理员的“运行与治理”已接入真实本地备份、OSS下载SHA-256核对、独立恢复和磁盘证据。生产5分钟采集保留既有夜间备份任务；日常备份沿用原恢复点目录，报告失败并在上传后执行下载核对，不删除历史备份。成员治理按维护者确认的策略启用：仅普通成员可停用／恢复，下一次联网请求生效，撤销旧会话；恢复须重新登录，计划和账号保留。

维护者在本版正式PC主动发送并确认收到管理员测试邮件后，已开启固定收件人的去重告警。故障与恢复事件持久防重、后台合并；不确定投递不自动重试。生产未人为制造故障，启用后收尾时告警投递表0条；这不等于真实故障邮件已被投递。清理仍只有预览，没有真实删除接口。

## 发布前数据库门槛

使用最终归档 `f83fe9f4c067de0c27480eb3410f4013d6ddc1b3213892c3cccb45e5af1eaed6`，API JAR为 `869daca03f6f60b94b5fd09f382e90791687aeef23b17c6b9dc931f72904106c`。早期归档和演练保留，未将旧JAR结果替代最终字节验证。

本次新鲜真实快照为旧发布目录下 `backups/plan_platform-mysql-20261019-075120.sql.gz`，独立恢复库 `plan_restore_20261019_075120`。V23→V24迁移及实际旧0.1.15镜像启动演练通过，36张原业务表全表校验一致；无用户登录、模型或邮件密钥挂载。服务器证据 `release-drills/roadmap-20261019075119-7fdabfbb/report.json` 为PASS。

正式切换前再备份并核对无活动AI任务。新目录 `/home/oyts/plan-platform/releases/0.1.16-f83fe9f4` 已健康启用，前一目录 `0.1.15-50bc92a`、旧镜像、Node服务、数据库卷、恢复库和备份均保留。V24仅新增邮件事件表。升级后日常备份链路实际生成 `plan_platform-mysql-20261019-080719.sql.gz`，私有OSS上传及下载比对通过。

Secure Cookie、AI只读Key、SMTP只读目录、时区、Windows静态挂载和Android私有挂载均保留。运行目录2750／GID101、元数据0640，后端只读挂载。宿主机Python3.6兼容修复仅涉及运维工具，不改变已验收的原生业务字节。后补采集安装器与日常备份helper单独部署并保留旧配置，最终归档没有被原地重写。

## 集中定向验收

共享源码的29个后端独立用例、14项连接层、20项最终桌面／手机网页检查，以及独立MySQL的V1—V24与38表合成恢复已通过。早期夹具／并发响应预期失败和两个真实验证路由问题均保留记录，修复后的范围见 [实现与证据](mail-and-operations.md)，没有重跑无关旧业务。

生产网页使用已有受邀合成普通账号，共6项通过：两种视口的邮箱验证状态／只读刷新、无效合成链接不退出、管理员及未登录边界。没有发送测试账号邮件、改变真实计划或调用付费AI。证据 `acceptance-results/public-web/20261019-080319-77e9e7/report.json`。

Windows在原目录同身份 `/UPDATE` 安装，启动前481个应用数据文件SHA-256不变。7项正式核对通过，包括新邮箱／运维／治理界面、版本、关闭重开会话、0/0队列及正式源检查更新／自动下载未勾选。**首次启动恰逢生产切换，显示502；维护者随后人工重新登录。** 后续重开保持登录，不将其声称为原登录会话跨升级保留。没有卸载、清队列或自动输入认证。证据 `acceptance-results/native-mail-operations-0116/windows-formal.json`，受限安装原记录 `.desktop-tools/formal-windows/private/20261019T075238Z-d3e39144/installation.json`。

Android实际安装APK与候选哈希相同，正式5项通过：原登录与0/0队列保留、邮箱状态、运维证据、治理策略、关闭重开。工具初轮将分组菜单误当底栏而失败，随后按真实菜单滚动修正；空密码账号设置仅允许显式只读，认证页和非空密码仍拒绝。失败记录保留，5项安全守卫及原6项工具守卫通过，未降低产品断言。证据 `acceptance-results/native-mail-operations-0116/after-20261019T080640445783Z.json`。

新增宿主机运行配置守卫5项、告警开关守卫4项、Windows发布工具8项通过；采集器4项保留直接受影响复验。运行配置测试初轮Windows路径分隔夹具不兼容，修正路径规范化后5项通过；Linux真实采集／备份链路亦通过。

## 安装包与更新源

正式候选 `production-artifacts/client-release/0.1.16/20261019T033904Z-3b682161/`：

- Windows：`windows/Plan Cabin_0.1.16_x64-setup.exe`，SHA-256 `f8c612d0b4ffe4f780d5100a33b8fe6baa894dc8bd72a65fd0b7536b0ffae3a2`。沿用长期更新签名，未配置受信任Authenticode发布者证书。
- Android：`android/PlanCabin-0.1.16-release.apk`，SHA-256 `beccfba5cf8c0722400fa6cd1e61323e220dfbe3b2e5c93e5979202c69197afc`。同包名 `cn.zijun701.plan.app`、同证书，非debug且明文网络关闭。

Windows公开下载和更新源完成真实HTTPS5项验证，包括两条EXE下载的签名／哈希、清单no-store、安装包不可变缓存、桌面与手机官网下载及Android公开关闭。证据 `acceptance-results/windows-public-cloud/2026-10-19T08-16-28-431Z-6c928e11/report.json`；本地发布目录 `production-artifacts/windows-public-release/0.1.16/20261019T081444403Z-uvftrM`。

Android受限源4项通过：匿名及无效令牌拒绝、公开APK／旧静态源404、两个受邀普通账号的私有清单与完整APK哈希。证据 `acceptance-results/invited-android/2026-10-19T08-09-33-786Z-da8ea259/report.json`。正式手机随后检查显示最新，自动下载未勾选，证据 `acceptance-results/native-mail-operations-0116/update-check-20261019T081123539070Z.json`。没有重新进行应用内跨版本下载安装，不声称证明非空队列跨升级保留。

## 回退与分发边界

生产回退须保留会话代次，0.1.15目标已在真实快照上验证。先使用新版 `check-governance-rollback.py --target-supports-generation yes`，通过后用新版 `production-compose.sh` 对保留的0.1.15目录恢复服务；核对健康后再切换current。不得清零代次、降级或回灌旧真实数据库。仅关闭治理开关不会恢复旧令牌。回退到更早镜像时不能照搬 `yes`。

Windows静态清单回退：`sh /home/oyts/plan-platform/client-public/releases/windows-0116-jOMfgN/publish.sh rollback windows-0116-jOMfgN`。Android私有源回退：`sh /home/oyts/plan-platform/android-private/staging/android-21-6b408795/rollback.sh`。它们保留安装包，不降级已安装应用或更改数据库。

告警开关的旧配置保留于服务器 `operations-mail-activation-20261019T082012737944Z/previous.yml`；运行采集／旧crontab及备份helper保留于 `operations-runtime-20261019T080628870741Z/`。不将这些受限资料上传GitHub。

GitHub公开附件仅限Windows EXE、其更新签名和校验和；APK、真实账号、数据库、备份和签名资料均不上传。默认AI20次／天和管理员热配置不变。Codex自动任务继续暂停，不自动安排下一窗口。下一完整开发阶段为AI规划工作台与手机输入体验，尚未开工。
