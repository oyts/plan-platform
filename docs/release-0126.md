# 0.1.26／Android31：核心冻结与三端发布

2026年10月29日，**Web/API、Windows官网／可信可选源及Android登录受限源均为0.1.26／Android31**，Flyway V34。正式双端已安装验收，队列均保持0项操作／0项冲突，实际检查新版本均显示最新且自动下载未勾选。PC保持原登录；Android曾停在认证页，由维护者本人重新登录后验证冷重开保持，不宣称Android全程免登录升级。[GitHub v0.1.26](https://github.com/oyts/plan-platform/releases/tag/v0.1.26)只归档Windows安装包、更新签名及校验和，实际归档状态以同名Release为准；不移动v0.1.25或替换旧附件。

## 本版实际修改

修复往周周复盘刷新／离线重开后回到当前周：把已选周保留在`/weekly-review?date=YYYY-MM-DD`，严格校验日期。切换同页日期前保护未保存输入，取消换周时保留原周和文字。不改数据库、业务API、私人存储键、应用身份／证书或视觉系统。

共享验收与缺陷证据见[阶段6检查点](core-freeze-progress.md)及[缺陷登记](core-freeze-defects.json)。后台契约、私有数据与核心链路证据按各轮准确范围使用，不重复累计。独立debug包不能当作正式安装包提供给朋友。

## 已完成的发布门槛

1. 独立PC6组／Android4组通过，包含本版往周重载、草稿恢复及关键入口；调试包不对外分发。
2. 原身份签名、字节／版本／可信源通过；新的生产快照62表双恢复、61张原业务表保持及旧25镜像兼容通过，四项回退能力守卫保留。
3. Web/API26健康，正式HTTPS5组通过；正式双端保留安装、只读队列及冷重开通过，Android本人重新登录的事实单列。
4. Windows公网5组、Android受限源4组通过，实际正式双端检查最新通过；GitHub源码和Windows-only附件在生产验收后归档。

公众注册、官网APK／二维码及必更继续关闭。不发真实测试邮件、不调用付费模型、不自动提交真实队列。系统覆盖安装不冒充本轮又执行跨版本应用内安装；该能力和合成非空队列升级沿用阶段5独立身份的精确证据。

## 最终修复候选和独立验收

最终候选`production-artifacts/client-release/0.1.26/20261028T140932Z-9ed9476a`包含CF-008修复。Windows更新签名及长期公钥验证通过，SHA256 `e8bbbb6c5757fa0f6bc3fc1460bad02eb0f0bb0d726c736c9c442569ad0fedf9`；正式Android包名／证书／31构建／私有更新源及SHA256 `4767453195c758865cb0a1e2ef5e2c7f53b6164ed5028f6ec67c091420597313`验证通过。没有新增Authenticode认证。

独立r2同源码Windows6组、Android4组通过，分别见`acceptance-results/core-freeze/pc-r2-observations.json`及`android-2026-10-28T14-08-05-921Z/report.json`。往周页面重载保留日期和本机文字；冷启动默认返回记忆工作区，按所选周链接可再读草稿，不承诺自动回到任意上次工具页。Android使用故障代理关闭全部现存TCP连接后确认离线保存禁用，不把移除USB转发但仍有长连接的结果当成断网证明。未操作正式计划或提交真实队列。

Web归档SHA256 `012dfda43e3de0fb86761ebb784c10a9217200e878712fb25a3f5c79c3df7895`，复用已验收API JAR `da8d50fbe22bac2ebf6ce9f84b5ce1bfa405476ab3e2d374f8c2462cc6434d22`，仅重建最终Web资源。ECS候选`/home/oyts/plan-platform/releases/0.1.26-012dfda4`，恢复演练及正式门槛见机器续做记录。

## 升级后备份与恢复

升级后新快照`plan_platform-mysql-20261028-142838.sql.gz`位于既有受限备份目录，SHA256为`99c45ef989b70b7ea1f751ec7b058d6fd1840febc3dcdbe850641797559a82fb`。OSS真实上传／下载往返核验通过；62张表分别恢复至`plan_restore_20261028_142841`与`_reference`并逐表比较通过。回退能力守卫、原采集计划和挂载保留。报告在正式26目录`post-release-verification.json`，生成时61个计划采样、最新37秒；这是当时状态，不代表当前样本新鲜度或生产容量保证。未写真实业务、发测试邮件或调用付费AI。

## 正式原生验收与分发

Windows在原目录同身份覆盖，启动前640个原数据文件校验未变。10月29日原账号和已有任务可见，同步0/0；正常关闭窗口后重开，原登录、任务和0/0队列保留，已关闭的版本亮点不重复。一次窗口坐标故障经重新绑定实际窗口及一次重试恢复，没有使用CDP或绕过认证。证据`acceptance-results/native-core-freeze-0126/windows-formal-observations.json`只记录脱敏结论，不保存真实任务正文。

Android25／30原登录和0/0基线在覆盖前记录，最终26／31精确APK覆盖成功。上次中断时出现认证页，本人于本轮登录原账号后，脚本四组通过：实际APK哈希、已认证工作区及原队列，安装版本和自动下载，历史入口，冷重开登录／0/0及亮点不重复。证据`after-20261029T010440054064Z.json`；不以此声称全程会话不失效或真实非空队列升级。

Windows静态发布`client-public/releases/windows-0126-ZQqZB8`保留旧不可变安装文件和原脱敏运行采集配置，只重建Web静态容器。公网5组核对HTTPS／注册限制、清单no-store、两条EXE精确哈希／签名及不可变缓存、Android公共拒绝、桌面和手机网页真实官网下载。证据`acceptance-results/windows-public-cloud/2026-10-29T01-06-52-660Z-37ec41b6/report.json`。正式PC再点检查更新显示最新，复选框实际为空。

Android私有发布`android-private/staging/android-31-0f5a9dd7`完成4组鉴权与精确字节检查：匿名／无效凭据拒绝、公共APK和二维码仍关闭、两名受邀普通账号的账号绑定清单及APK哈希。证据`acceptance-results/invited-android/2026-10-29T01-06-15-199Z-7b6c9245/report.json`。正式手机再补2组受限源检查，最新26且自动下载未勾选，0/0队列不变；见`native-core-freeze-0126/update-check-20261029T010753047949Z.json`。

## 不可变归档与阶段结论

GitHub `v0.1.26`已公开归档，标签指向`3f71b4bfedc9feb8a4693dc5f120acb15ade5b95`。附件为`Plan.Cabin_0.1.26_x64-setup.exe`、对应`.sig`及`SHA256SUMS.txt`，上传API的大小和digest逐项核验通过；GitHub将空格规范为点，字节不变，没有APK、数据库、凭据或测试原文。证据`acceptance-results/core-freeze/github-0126-publication.json`。后续纯资料提交不移动该标签，不重制已发布包。

本轮六阶段完成，约定核心功能可作为下一大阶段UI重构的业务基线；没有本轮范围内未关闭的P0／P1或阻断核心流程的P2。路由、状态、深链、身份和数据保护要求见[UI交接](ui-rebuild-handoff.md)。这不是“所有设备永无BUG”、1.0、公开APP上架或备案完成的声明，也不自动启动视觉重构。

## 回退和后续维护

当前正式目录`/home/oyts/plan-platform/releases/0.1.26-012dfda4`，上一版本`0.1.25-4be0a01f`、旧容器／镜像／卷和全部备份保留。Web/API回退必须先通过generation、long-planning、admin-runtime、metrics四项会话代次能力守卫，再以既有`production-compose.sh`启动上一套Web/API并确认健康，最后切换current；不降级或覆盖真实数据库。独立演练已验证兼容，本轮未实际回退现网。

Windows下载入口可用新静态目录`publish.sh rollback windows-0126-ZQqZB8`恢复前次清单和挂载；Android私有源可用上述staging中的`rollback.sh`恢复前次清单。两者都不卸载／降级已安装应用，不删除新包或业务数据。不能盲目重跑安装、激活或有CREATE_NEW保护的恢复脚本。

## 已完成但现已过期的候选打包（禁止发布）

早期真机补测发现CF-008：原生延后恢复认证时，周复盘重载被重定向到最近工作区。严格白名单周复盘深链修复已包含在最终140932候选并实际补验。以下字节早于该修复，**只能保留追溯，不能安装到正式应用或发布更新源**，不得覆盖这些旧候选。

正式候选目录`production-artifacts/client-release/0.1.26/20261028T075350Z-0ed65245`。Windows更新签名验证通过，SHA256 `3e5cae68c0715009bf7553df00913d6d2ae50dd02778d52d74c5f00346e4dd63`；Android正式身份`cn.zijun701.plan.app`／31、固定证书、非debug、禁止明文及登录受限更新源验证通过，SHA256 `60b0df924a623fcc75ca4e88570e00db33de38c3ff8cc908f2751e3f3f004dad`。Windows没有新增Authenticode认证，不把更新签名称作已认证发布者。

早期首次Android release构建因代理访问官方Maven Central返回403失败，保留已成功Windows产物，仅用既有`-DirectMavenCentral`补构建成功；没有改镜像源、TLS或签名身份。早期075350候选没有用于正式安装或分发；最终采用另建的140932候选。7项发布身份／签名守卫通过，[机器状态](release-0126-resume.json)记录实际发布门槛。
