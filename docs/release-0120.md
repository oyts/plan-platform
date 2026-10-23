# 计划舱0.1.20：历史问题收口

2026年10月23日，正式Web/API、Windows官网及可选更新源、Android登录受限更新源统一发布 **0.1.20／Android构建25**。双邮箱变更已按维护者确认启用；不会自动改变管理员或任何成员的邮箱。公开注册、官网APK／二维码保持关闭，更新不强制，自动下载默认不勾选。

## 本版完成什么

- 旧版回收站迁移：新导入支持计划、路线、步骤、标签四类快照；永久删除标记抑制旧副本，不伪造没有正文的恢复项。已完成的旧批次可以先只读预检，再明确确认补迁；指纹、版本、所有权与持久回执保证原子性和幂等，不覆盖导入后的编辑。回滚本批仍涵盖原导入及补迁，不是单独撤销补迁。
- 邮箱变更：当前密码、新旧邮箱两侧主动验证、最终再次确认密码；30分钟令牌只存摘要，邮件预取不会自动验证，不抄送验证链接。成功后旧访问／刷新会话和提醒授权失效，计划及本机队列不删除。独立环境的真实双邮箱收信与本人确认已通过，正式账号未被更改。
- Windows导出：仅CSV／JSON／Markdown、最多32MiB的系统“另存为”，取消不写文件、不显示成功。真实JSON保存／取消、CSV中文与UTF-8 BOM已核对；系统保存窗口定位不可靠时由维护者选择路径，不能称为全自动保存。
- 提醒与分享补验：独立Android包已验证锁屏联网核对、通知点击、离线队列抑制和撤回，以及重启后授权保留、新鲜核对与未来通知；合成JSON经QQ“我的电脑”接收后哈希一致。不读取其他聊天文件，不修改正式计划。
- API契约：190个MVC操作、声明DTO和经审查的动态正常响应；共同失败包络按实际Advice／过滤器区分。旧JSON采用明确的宽容接收约定及schema1—6标准字段指南，不增加拒绝历史文件的严格验证器，也不宣称穷尽所有网络／安全／容器错误。

详细实现与早期失败证据见[历史收口记录](historical-closure-progress.md)、[账号安全](account-security.md)、[旧版迁移](legacy-migration.md)和[核心API](core-api.md)。

## 本次定向验证

日常版本只验证新增及直接受影响功能，没有重跑无关全量业务。以下数量按不同用例计，不累加重跑次数。

| 范围 | 实际结果与证据 |
| --- | --- |
| 后端 | 63项，0失败／错误，含旧迁移、双邮箱、原邮箱验证、限流、API路由与错误契约；`acceptance-results/historical-closure/release-0120-build.log` |
| 前端 | 导出及AI草稿直接受影响16项通过，同一构建日志 |
| 浏览器 | 桌面／手机视口共12项通过，正常退出0；`browser-0120-owner-final.log`。较早运行全部断言通过但子进程清理挂起的日志保留，不冒充完整管线通过 |
| 契约补核 | 最终5项通过，`contract-0120-refs-final.log`；旧JSON六版本和非对象输入共9项已计入上述63项，不再次累计 |
| 发布工具 | 快照守卫5项、激活守卫4项、提醒时钟3项通过 |
| 正式HTTPS | 已有普通合成账号、桌面／手机视口共6组通过；`acceptance-results/public-web/20261023-093513-f128f3/report.json`，无业务写入、真实发信或付费AI |
| 正式Windows | 原目录同身份覆盖；启动前556个原数据文件不变；原登录、0/0队列、邮箱／补迁入口、版本、冷重开及发布后实际检查更新通过；`acceptance-results/native-historical-0120/windows-formal.json` |
| 正式Android | 同包名同证书0.1.19／24→0.1.20／25覆盖；精确APK、原登录、0/0队列、新入口与冷重开4组通过；发布后更新检查2组通过；`acceptance-results/native-historical-0120/` |
| Windows公网分发 | 5组通过：下载字节／签名、可信更新源、旧包保留及官网布局；`acceptance-results/windows-public-cloud/2026-10-23T09-48-05-787Z-08fbe262/report.json` |
| Android受限分发 | 4组通过：匿名及无效凭据拒绝、两名受邀成员的清单／APK哈希、公开APK仍关闭；`acceptance-results/invited-android/2026-10-23T09-43-02-807Z-7ffe0804/report.json` |

正式双端本轮均为空队列，Windows原计划列表为空，不能声称本轮验证了真实非空队列或某份真实计划内容。非空队列与完整新业务交互来自此前独立合成验收，不互相替代。覆盖安装不等于本轮重新执行应用内跨版本下载安装。Windows更新签名不是Authenticode发布者证书。

此前真实双邮箱仅发送维护者批准的两封邮件；本次版本构建、生产切换与正式只读验收 **主动测试邮件0、付费AI调用0**。现有运维告警策略未因此关闭。

## 精确发布物

本机正式候选目录：`production-artifacts/client-release/0.1.20/20261023T091841Z-57e81c7c`。

| 发布物 | SHA-256 |
| --- | --- |
| Windows `windows/Plan Cabin_0.1.20_x64-setup.exe` | `974df4aa8abb43f12c85c60d289849a86b4f04042a6eb2f806978df7500ef33f` |
| Android `android/PlanCabin-0.1.20-release.apk` | `3ba9f4020e4c3c614c87b252a0835de50b20e37da4c361120a37d3b7a6cf579d` |
| API `backend/target-acceptance/plan-platform-api.jar` | `f46a8a7efd93e209be853bf991949fc837c88ba250b9be4499983e3a8d7bf352` |
| 云端 `production-artifacts/plan-platform-0.1.20.tar.gz` | `b1a787012f5785b52fbd0b2950352309331a89a87f085b72d9b597aa6a8ce88f` |

Android为`cn.zijun701.plan.app`，构建25，3517476字节；原证书指纹`4528eeee02c43df6a4429481c99f6c39d459c82d9ca1c538f316e2ff37c68e9d`不变，不可调试、不允许明文正式请求。没有更换应用身份、卸载或清理账号与队列。签名私钥及加密备份不上传GitHub。

## 生产备份、迁移与回退

- 当前发布目录：`/home/oyts/plan-platform/releases/0.1.20-b1a78701`，Spring Boot健康返回0.1.20，Flyway到V30。旧发布、镜像、MySQL卷、备份和旧Node服务保留。
- 升级前真实不可变快照独立恢复两次，各49张表校验一致；V28→V30后48张原业务表投影不变，两张新增表为空；实际旧0.1.19镜像启动兼容通过。不是用另一时刻的可变生产库作比较，也不把此前合成51表恢复混成本次49表快照恢复。
- 演练报告：`/home/oyts/plan-platform/release-drills/roadmap-20261023092757-cf92e5ec/report.json`。快照：`/home/oyts/plan-platform/releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261023-092758.sql.gz`。
- 激活脚本先检查演练／归档／JAR哈希和旧版本，再做新的激活备份并切换；只在新发布环境启用`EMAIL_CHANGE_ENABLED=true`，旧环境未修改，凭据通过既有受限只读挂载使用。
- 升级后日常备份`plan_platform-mysql-20261023-094825.sql.gz`（213484字节）已上传既有私有OSS并下载校验；恢复演练状态绑定本次实际通过的快照。没有删除任何恢复点或新发测试邮件。

业务入口如需回退，先运行`python3 scripts/check-governance-rollback.py --target-supports-generation yes`，再用`sh scripts/production-compose.sh /home/oyts/plan-platform/releases/0.1.19-3293e8c5 up -d --no-deps backend web`恢复已验证的旧镜像及原环境。确认HTTPS健康为0.1.19后，才将`current`以同目录临时链接原子切回该旧目录。激活脚本的异常路径包含同一守卫和恢复过程；不降级／覆盖数据库，不自动撤销已确认的邮箱变更。**不要使用`rollback-public-entry.sh`**：该早期脚本是回到旧Node入口，不是本次Spring版本回退。

静态分发的独立回退路径保留：

```sh
# Windows，仅在需要撤回本次分发时执行，不是日常验证命令
sh /home/oyts/plan-platform/client-public/releases/windows-0120-2iWCaB/publish.sh rollback windows-0120-2iWCaB
# Android，恢复先前登录受限清单，不开放匿名下载
sh /home/oyts/plan-platform/android-private/staging/android-25-70dde9cc/rollback.sh
```

## 归档与保留边界

GitHub使用不可变标签`v0.1.20`，只归档Windows EXE、更新签名和SHA256SUMS；私测APK不上传公开Release。归档实际状态与提交见机器检查点`docs/release-0120-resume.json`。

本轮约定的历史功能及验收缺口已收口，但不承诺关机、系统强停、所有厂商冻结场景通知必达，不把兼容字段指南当完整历史文件严格Schema，不将网络异常穷尽化。APP／公安备案、1.0命名、公众注册、公开Android下载、团队协作和小程序仍须另行规划或确认。自动续做保持关闭，额度不足直接通知维护者。
