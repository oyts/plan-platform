# 计划舱六阶段版本发布记录

2026 年 10 月 18 日，正式 Web/API、Windows 官网下载和可选更新源已发布为 **0.1.15**。Android 登录受限源仍为 **0.1.14／19**，本版候选为 **0.1.15／20**，等待用户稍后连接手机并完成正式覆盖验收。公开注册、官网 APK 和二维码仍关闭；不是双端均已发布的声明。

## 新增内容与已完成验收

反馈与脱敏诊断、个人数据中心、结构复制与未来编排、路线暂停与历史查询、离线草稿与人工冲突比较、帮助与本地演示均已上线网页。运行证据与成员治理能力已随代码部署，但生产开关继续关闭，真实告警、清理及治理策略须另行确认。

共享源码、独立 MySQL、独立 Windows 九组及 Android 真机十一组新增业务的结果和边界见 [六阶段续做记录](roadmap-progress.md)。正式站本轮只集中检查新增业务与直接影响的链路，使用已有两个受邀合成普通账号，桌面及手机网页视口共八项全部通过：

- 反馈的明确同意、诊断白名单、私有响应与账号隔离。
- 单条合成待办备份预检、确认后恢复为新副本、重复确认防重与实际 CSV 下载。
- 周期结构复制、未来批量调整的真实409拒绝、原日期保留与他人不可读。
- 七天日程预加载、帮助与版本、普通用户访问管理员接口被拒绝。

证据为 `acceptance-results/public-web/20261018-131334-1a605a/report.json`，八项无失败，已检查真实页面截图。只向测试账号新增合成记录，没有改管理员或朋友的计划，没有真实邮件或付费 AI。手机网页视口不算正式 APK 验收。

## 数据库与云端升级

发布目录为 `/home/oyts/plan-platform/releases/0.1.15-50bc92a`，旧目录 `/home/oyts/plan-platform/releases/0.1.14-328d399`、旧镜像、旧 Node、数据库卷和备份均保留。部署归档 SHA-256 为 `792a5d8a412d6e650833592043d87de9f31aa9a9e146e7b390bc374fa310b1b6`，最终 API 为 `6a68b763d7b05c48aaaae379eb7ba9eb313840ab28e79b4b3333039823690013`；后续验收工具提交不改变这些已验收资源。

先生成真实生产备份 `plan_platform-mysql-20261018-130807.sql.gz`，恢复至独立库 `plan_restore_20261018_130808`，演练 V19→V23 及实际 0.1.14 镜像回退启动。27 张原业务表校验通过；对新增列的 users、task_templates、mainlines 逐项比较所有原列的投影指纹，而不是跳过数据。演练没有公网端口、用户登录、AI／邮件密钥、生产服务切换或数据清理。证据为服务器 `release-drills/roadmap-20261018130806-fcd62765/report.json`，PASS，容器已停止，恢复库保留。

正式切换前再次备份 `plan_platform-mysql-20261018-131224.sql.gz` 并检查没有排队／运行中的 AI 请求；该备份和 SHA-256 已上传既有私有 OSS，再下载到新受限目录验证相同哈希及 gzip 完整性，原备份保留。下载核对证据在发布目录的 `backup-download-check/result.txt`。

正式 HTTPS 健康为 `plan-platform-api 0.1.15`，邀请和找回密码保留，注册关闭；Secure Cookie、AI 只读 Key 挂载、邮件配置、时区及两个客户端更新挂载均保留。运维和成员治理保持关闭，没有改变默认 AI 次数或触发真实调用。

## Windows 正式升级和发布

用户保存关闭并明确允许后，在原目录 `E:\PlannedCabin\Plan Cabin` 用 `/UPDATE` 覆盖0.1.14→0.1.15，没有卸载或清数据。安装前先保存旧程序与应用数据，启动前452个原文件逐项 SHA-256 不变，未导出或修改凭据管理器。受限记录为 `.desktop-tools/formal-windows/private/20261018T132203Z-76d2ebd0/installation.json`。

实际正式窗口八项只读核对通过：原登录保留、安装后空队列0/0、原 HTTPS 地址及 Rust 原生连接成功、新数据中心及原计划选择器加载、页面／安装／服务器均0.1.15、正常关闭重开仍登录、重开后队列0/0，以及发布后原生更新检查正常／自动下载未勾选。没有改真实业务、提交队列、自动填写认证窗口；不声称证明了非空队列跨升级保留或本版应用内跨版本安装。脱敏结果为 `acceptance-results/native-roadmap-0115/windows-formal.json`。

重新核对既有签名备份的实际恢复证明、独立口令保管确认和网盘加密下载副本的大小／哈希后，发布 Windows-only 静态源。旧版本 EXE 与签名逐字节保留，可选更新、最低版本0.0.0不变；官网 Android 为 null、公开 Android 清单与APK404、公众注册关闭。静态回退材料为服务器 `client-public/releases/windows-0115-3HKpk2/`，回退仅影响下载清单，不降级已安装程序或数据库。

实际 HTTPS 五项检查全部通过：健康／注册关闭／HTTPS跳转，下载与更新清单no-store、两条真实EXE下载哈希与长期签名、不可变文件缓存，Android公开关闭，以及桌面／手机手机官网实际下载。证据 `acceptance-results/windows-public-cloud/2026-10-18T13-26-02-922Z-4dadbe3a/report.json`。Windows准备目录为 `production-artifacts/windows-public-release/0.1.15/20261018T132526316Z-bzeUzq/`。这是公开下载与覆盖安装验收，不冒充新一轮跨版本应用内安装。

## 签名候选与待完成手机安装

正式候选位于 `production-artifacts/client-release/0.1.15/20261018T075011Z-49f03c07/`，发布前再次以原公钥／证书实际核验 Windows 更新签名、APK 签名、包名、构建号、禁止调试和明文网络，以及内嵌登录受限更新源。新核验证据在候选目录的 `prepublish-check-20261018/`。

- Windows 安装包 `windows/Plan Cabin_0.1.15_x64-setup.exe`，SHA-256 `52067147ea37c147bb6362b3e4aa26c6223b39fdae95326b9e1bbc4a15509476`。Windows 更新签名不等于 Authenticode 发布者证书。
- Android 安装包 `android/PlanCabin-0.1.15-release.apk`，SHA-256 `19fd4815f6477716b4eea170e30b6f381b82589449a2d04d9f0b45e6a448e9ee`。沿用 `cn.zijun701.plan.app` 原包名及证书，不卸载、不清数据。

Android 仅准备了本地受限发布目录 `production-artifacts/invited-android/0.1.15/candidate-LkoG5B/`，没有上传或激活；最低构建号仍为1，属于可选更新。用户本次表示稍后连接手机，因此没有枚举或操作手机、覆盖正式 APK 或切换 Android 更新源。

## 下一步与回退边界

1. Windows正式覆盖与公开源已完成，不重复安装或重跑无关业务；正式客户端保留在版本页供用户查看。
2. 用户连接并解锁手机、明确同意覆盖后，记录升级前队列，使用同包名同证书覆盖安装并由本人处理系统提示。验证实际 APK 字节、原登录、数据与队列、重开和本次新增入口，不清队列或代填密码。
3. 手机安装保留与云端门槛通过后，发布 Android 登录受限源，实际 HTTPS 验证哈希、私有缓存及未登录拒绝。Windows不重复发布，官网 Android仍为null，公开静态 Android清单404，自动下载默认关闭。
4. 完成后归档 `v0.1.15` 和 GitHub Windows 附件；不上传私测 APK、私有验收资料、数据库或签名密钥。

生产应用回退必须先运行新版 `scripts/check-governance-rollback.py --target-supports-generation no`。通过后可用新版 `production-compose.sh` 对 `previous-release` 目录执行 `up -d --no-deps backend web`，验证健康后恢复 current 指针；不覆盖或降级数据库。若任一账号已提高会话代次，旧镜像回退会被拒绝，不能清零代次绕过。本次 `activate-roadmap-release.sh` 对失败自动回退也执行同一守卫。
