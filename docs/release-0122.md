# 计划舱0.1.22：完整大纲、分批细化与外部AI协作

2026年10月24日，**核心阶段2开发、定向验收及三端0.1.22／Android27正式发布完成**；Flyway V32。Windows官网／可选更新源和Android登录受限源均已验证。[GitHub源码与Windows附件](https://github.com/oyts/plan-platform/releases/tag/v0.1.22)单独归档，最终状态见release-0122-resume.json。

## 用户得到什么

- 范围较大的规划可先生成完整大纲，核对日期、阶段、依赖和覆盖范围，再逐批细化，每批最多10项。不会后台连续调用，未全部完成前不能应用到真实业务；也可手动补齐。
- 当前选中草稿可以准备外部AI协作JSON，修改后导回，先只读查看差异，再明确保存草稿；应用到真实计划仍是另一步确认。独立工作区备份和协作包是不同格式。
- 桌面每侧保留一个主要滚动区，手机切换对话／草稿；长需求笔记保存、键盘、横竖屏和返回输入保留按实测修正，不提前重构品牌。

容量：100事项／30阶段／128000字草稿；消息和需求笔记各8000字；工作区240KiB。每流程最多12次明确提交、24次上游尝试，保守预留输入1536000、输出98304 Token、计算1440秒，7天内且不晚于会话到期。不是一次输出或任意大上下文保证；失败／未知结果不会自动退回工作流预算或自动重试。本阶段没有新增付费AI或真实邮件验收。

## 已验证的范围

- 最终0.1.22后端74个不同用例（64业务＋10契约，196个路由）；前端27项；新增Web12项；受影响Web32项。旧滚动测试2项按新单滚动契约修正后重验通过，不把重跑累计成新用例。
- 独立PC：两批、预算、重载／冷重开、协作文件预检／差异／明确保存通过。系统文件保存和选择部分由维护者接手，不声称全自动。
- 独立Android：先前2组核心流程，随后4组重开／真实键盘／横竖屏／系统文件选择与取消，另1组真实系统分享面板与取消通过。分享缓存字节与选中材料一致；未对外发送。前台守卫和测试脚本beforeunload失败的原日志保留。
- V31→V32合成MySQL：51原业务表字段不变、含合成新增数据55表整库恢复、三模式批次／重放／409／隔离／预算持久化通过。最终HTTP容量修正JAR另补真实MySQL启动与超过256k字节中文请求，2组通过。
- 当时生产一致性快照：同一份快照两次独立52表恢复；迁移后51原业务表列投影不变，V32新增表为空，实际0.1.21旧镜像只读兼容。演练中不切生产。
- 正式Android：0.1.21／26→0.1.22／27，精确APK、原登录及0/0队列、大纲与协作入口、冷重开4组通过。正式Windows启动前573个原数据文件不变；首次打开遇到云端重启502，恢复连接后由维护者自行登录，**不能宣称跨升级会话自动保留**。后续只读及重开另记。
- 正式HTTPS桌面／手机6组：合成大纲两批手动完成、未完成保护、409、他人404、差异预检／明确保存、no-store、额度不变。未调用模型、未应用真实业务。
- 正式PC重新登录后真正关闭EXE并重开，登录保留；工具坐标故障后由维护者核对0/0队列及最新0.1.22，最后只读截图再次核对自动下载未勾选。Android受限源发布后原生更新检查2组通过，最新版本及默认不自动下载。Windows公网5组、Android受限源4组通过。

## 精确不可变候选

目录production-artifacts/client-release/0.1.22/20261024T113905Z-bc422b46。

| 产物 | SHA256 |
| --- | --- |
| Windows EXE | 388db10db159b6c6c7464be808ab5ad18cb0ca033b781955c1fcba1126f1fc58 |
| Android APK | 7c65fc9bbf009c43a75f087b818a63692cbc061aaf9d7a184d2faf7d695acb7f |
| API JAR | c3ba488184f2738495d5dd39839fd35b8671b818755edc6d013f3c7312c14b71 |
| 云端归档 | 5b962fb870806b1705b9d270d3c298662059885cefce3878ca30ab488233f26c |

Windows沿用长期更新签名，不等于Authenticode发布者证书。Android仍cn.zijun701.plan.app、原证书、非debuggable、禁止明文流量。正式空队列证据不证明非空队列跨升级；独立合成证据分开保留。

## 生产与回退

新目录/home/oyts/plan-platform/releases/0.1.22-5b962fb8；旧目录0.1.21-459347da保留。演练/home/oyts/plan-platform/release-drills/roadmap-20261024114624-bc99871f/report.json；快照plan_platform-mysql-20261024-114625.sql.gz，升级后备份plan_platform-mysql-20261024-115552.sql.gz，均在既有受限备份目录。后者通过现有OSS上传／下载字节校验。

精确激活脚本activate-ai-long-release.py要求一小时内快照证明与上述字节，保留AI／SMTP只读挂载、邮箱变更、成员治理、HTTPS和分发叠加配置。存在V32长流程或outline_only后，**禁止直接回退旧0.1.21后端写服务**；check-governance-rollback.py同时检查会话代次与长规划协议。旧镜像可读不是可安全写回退的证明。守卫不通过时保留新后端和数据，修复或只回退兼容前端，不降库、不删除流程绕守卫。不要使用早期回旧Node脚本。

公开注册、官网APK／二维码关闭；Android只登录受限分发。更新可选，自动下载默认关闭。源码、Windows附件可公开；APK、测试账号、报告原文、数据库、签名私钥和备份不上传GitHub。

Windows阶段production-artifacts/windows-public-release/0.1.22/20261024T115804492Z-aSgaSF，必要时在ECS执行`sh /home/oyts/plan-platform/client-public/releases/windows-0122-k1CLYV/publish.sh rollback windows-0122-k1CLYV`，只回退静态清单和挂载。Android阶段production-artifacts/invited-android/0.1.22/candidate-tVHoII，静态回退`sh /home/oyts/plan-platform/android-private/staging/android-27-c6141ed2/rollback.sh`。均不降级已安装客户端或数据库。旧安装包保留。

公网证据acceptance-results/windows-public-cloud/2026-10-24T12-01-05-152Z-7b43f16e/report.json、invited-android/2026-10-24T11-58-38-552Z-859e457b/report.json，正式双端native-ai-long-0122/。本轮同身份覆盖不冒充应用内跨版本安装测试。

## 证据

细节见[阶段2检查点](ai-long-planning-progress.md)。本轮新增报告位于acceptance-results/ai-long/、ai-long/android-followup/、native-ai-long-0122/、public-web/20261024-115415-8b6a0e/。私有运行资料由Git忽略，不将敏感原文抄入文档。
