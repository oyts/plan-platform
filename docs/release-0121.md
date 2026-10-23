# 计划舱0.1.21：核心阶段1 AI可靠性

2026年10月24日，**正式Web/API、Windows官网／可选更新源、Android登录受限源已统一0.1.21／构建26**。本轮核心阶段1开发、验收、发布及[GitHub归档](https://github.com/oyts/plan-platform/releases/tag/v0.1.21)均已完成。进度以[检查点](release-0121-resume.json)为准，不将独立调试包当成正式安装包。

## 交付范围

交流、生成和修订明确分离；独立需求笔记持续保留；输出已知无效时最多一次有界修复；父请求最多成功扣一次用户次数，未知结果不自动重试；每次尝试有脱敏原因／长度／用量／耗时，管理员不读取私人正文。手机展开需求笔记时保存按钮不再被后续输入表单覆盖。已有对象保护、409和明确应用继续保留。详见[阶段1验收](ai-reliability-progress.md)。

更新可选、自动下载默认关闭；公众注册、官网APK／二维码仍关闭。Android只供受邀成员在登录后更新，不上传公共GitHub附件。不更换长期签名或应用身份。

## 已通过的候选门槛

- 阶段开发：66项后端、10项契约、13个不同前端单元、6项新Web及22项受影响Web。10月24日最终移动保存修复后新Web6项重跑通过；独立Android5组及PC保存／诊断／修订／重开通过，不累加重跑成新用例。
- 有界官方模型样本：7父请求／8次上游／9847输出Token，原六项严格检查5/6和另一次推荐补验分别保留；本次发布不新增付费调用或真实测试邮件。
- 0.1.21后端最终：76个不同用例通过。第一次参数引用错误未执行测试；第二次75通过、1个契约版本漂移失败，审查确认仅版本字段后补跑10项契约通过并生成JAR。不伪造首次即全通过。
- 快照演练守卫5项、激活守卫4项通过。最初unittest discover对带连字符文件名发现0项，不算通过数；改为直接运行文件取得上述真实结果。
- 正式Windows已覆盖原目录，启动前561个原数据文件不变；原登录、0/0队列、0.1.21和HTTPS设置／默认不自动下载、冷重开及新版讨论／生成入口均通过；发布后实际检查显示当前最新版本，未勾选自动下载。
- 正式Android同包名同证书覆盖，精确APK、原登录及0/0队列、新版讨论／生成、对话／草稿切换、冷重开4组通过；发布后实际更新检查2组通过，显示当前最新且自动下载未勾选。
- 正式HTTPS桌面／手机共6组通过，需求保存、不调用AI、409、他人404、no-store、重开手动草稿与明确确认门槛均使用专用普通合成账号；没有调用模型或创建真实业务。Windows公网5组及Android受限源4组通过，旧安装包保留。

证据根目录：acceptance-results/historical-closure/ai-reliability-0121-*及acceptance-results/native-ai-reliability-0121/。私有资料不进Git。正式队列为空，不能声称证明非空真实队列升级；独立合成证据另列。

## 精确候选（不可重打旧版本）

目录：production-artifacts/client-release/0.1.21/20261023T192420Z-e73d16fb。

| 产物 | SHA256 |
| --- | --- |
| Windows安装包 | 05080068f30c456c48f2786d53f36066b358c26b7201f907b33bfd71475332f2 |
| Android正式APK | 93b071d63ad392669ce61a96d2d309b3500b575205fb169958ffceeea1e21e6e |
| API JAR | b19f89f94fcebcb0c50d51a8151f7c558ee2c99dd4a509473fe64605b7512737 |
| 云端归档 | 459347da4c535141abf8174c17be5eee8617494e79269b2be03f0a0b8ed16841 |

## 生产和回退门槛

新目录/home/oyts/plan-platform/releases/0.1.21-459347da，旧目录/home/oyts/plan-platform/releases/0.1.20-b1a78701。演练使用当时真实一致性快照，两次独立恢复，再检查V30→V31三张扩展表的原列投影与其他原表、空新增尝试表及默认值、实际0.1.20旧镜像兼容。正式服务在演练中不切换。

实际演练已通过：51表两次完整恢复一致，50张原业务表的原字段不变。报告/home/oyts/plan-platform/release-drills/roadmap-20261023193133-f1c39909/report.json；快照/home/oyts/plan-platform/releases/0.1.15-50bc92a/backups/plan_platform-mysql-20261023-193134.sql.gz。随后精确激活通过，公网健康0.1.21、功能／邮件／治理开关及只读挂载保留；升级后的日常MySQL备份已上传既有OSS并下载校验，无删除、无主动测试发信。

激活必须使用scripts/activate-ai-reliability-release.py，核对精确归档／JAR与一小时内演练证明。只在新发布.env启用AI_REPAIR_ENABLED=true，旧发布环境和共享叠加文件保持原字节。邮件变更、SMTP／AI只读挂载、治理／告警、HTTPS与受限分发挂载保留。实际启用后默认60秒、合计8192输出Token及64K保守输入字节边界继续由后端执行；不因启用修复增加用户每日次数。

激活失败先执行会话代次回退守卫，再恢复旧应用镜像／入口，不降级数据库、不清账号或队列。不要使用早期回旧Node的rollback-public-entry.sh。保留旧包、镜像、卷、恢复库和备份；未测试通过不发布更新源。

本轮5小时额度剩余5%时保存并通知维护者，不安排自动任务。尚未开始阶段2—6或UI重构。

## 分发与回退

Windows阶段：production-artifacts/windows-public-release/0.1.21/20261023T194352009Z-Q1fSbp，远端client-public/releases/windows-0121-308Q95；必要时执行该目录publish.sh rollback windows-0121-308Q95，仅撤回静态清单。Android阶段：production-artifacts/invited-android/0.1.21/candidate-7d5uaS，必要时执行/home/oyts/plan-platform/android-private/staging/android-26-c3f38f94/rollback.sh。两者均保留新旧字节，不降级已安装客户端。

公网证据：acceptance-results/public-web/20261023-194106-2cb41c/report.json；acceptance-results/windows-public-cloud/2026-10-23T19-44-47-543Z-0367572f/report.json；acceptance-results/invited-android/2026-10-23T19-44-58-068Z-93e6f45f/report.json。Windows更新签名不是Authenticode发布者证书；本轮同身份覆盖不冒充重新执行应用内跨版本安装。

不可变标签v0.1.21指向81b90316baa61d84934615d64c12bdabfdc15ac2。GitHub仅公开Windows EXE、更新签名和SHA256SUMS；APK、账号、日志、数据库及密钥均未上传。后续仅资料一致性提交可以领先标签，不移动标签、不重打已发布包。
