# 0.1.26／Android31候选：核心冻结收口

当前状态：**仅候选，尚未正式发布**。正式Web/API、Windows和Android仍为0.1.25／30。维护者已批准综合与双端验收通过后继续发布，但正式双端保存／覆盖仍需届时本人确认。不得移动v0.1.25或替换其不可变附件。

## 本版实际修改

修复往周周复盘刷新／离线重开后回到当前周：把已选周保留在`/weekly-review?date=YYYY-MM-DD`，严格校验日期。切换同页日期前保护未保存输入，取消换周时保留原周和文字。不改数据库、业务API、私人存储键、应用身份／证书或视觉系统。

共享验收与缺陷证据见[阶段6检查点](core-freeze-progress.md)及[缺陷登记](core-freeze-defects.json)。后台契约、私有数据与核心链路证据按各轮准确范围使用，不重复累计。独立debug包不能当作正式安装包提供给朋友。

## 发布门槛（尚未完成）

1. 独立PC／Android核心路径及本版周复盘修复实际验收。
2. 原身份正式签名候选、字节和版本／证书／可信源核验；新的当时生产快照与隔离恢复、旧镜像兼容及可执行回退。
3. 升级Web/API并确认健康、登录与受影响路径；正式双端本人保存授权后覆盖、只读登录／队列与冷重开。
4. 发布验收通过的Windows公开可选源与Android登录受限源，真实检查最新版本且自动下载未勾选；GitHub源码及Windows-only附件归档。

公众注册、官网APK／二维码及必更继续关闭。不发真实邮件、不调用付费模型、不自动提交真实队列。生产切换和正式安装尚未执行；独立双端已补验，见下文。

## 最终修复候选和独立验收

最终候选`production-artifacts/client-release/0.1.26/20261028T140932Z-9ed9476a`包含CF-008修复。Windows更新签名及长期公钥验证通过，SHA256 `e8bbbb6c5757fa0f6bc3fc1460bad02eb0f0bb0d726c736c9c442569ad0fedf9`；正式Android包名／证书／31构建／私有更新源及SHA256 `4767453195c758865cb0a1e2ef5e2c7f53b6164ed5028f6ec67c091420597313`验证通过。没有新增Authenticode认证。

独立r2同源码Windows6组、Android4组通过，分别见`acceptance-results/core-freeze/pc-r2-observations.json`及`android-2026-10-28T14-08-05-921Z/report.json`。往周页面重载保留日期和本机文字；冷启动默认返回记忆工作区，按所选周链接可再读草稿，不承诺自动回到任意上次工具页。Android使用故障代理关闭全部现存TCP连接后确认离线保存禁用，不把移除USB转发但仍有长连接的结果当成断网证明。未操作正式计划或提交真实队列。

Web归档SHA256 `012dfda43e3de0fb86761ebb784c10a9217200e878712fb25a3f5c79c3df7895`，复用已验收API JAR `da8d50fbe22bac2ebf6ce9f84b5ce1bfa405476ab3e2d374f8c2462cc6434d22`，仅重建最终Web资源。ECS候选`/home/oyts/plan-platform/releases/0.1.26-012dfda4`，恢复演练及正式门槛见机器续做记录。

## 已完成但现已过期的候选打包（禁止发布）

真机补测随后发现CF-008：原生延后恢复认证时，周复盘重载被重定向到最近工作区。已补严格白名单周复盘深链恢复，6项相关单测和类型检查通过，尚待新包实际补验。以下字节早于该修复，**只能保留追溯，不能安装到正式应用或发布更新源**。下一轮必须在全新目录重建签名包；不得覆盖这些旧候选。

正式候选目录`production-artifacts/client-release/0.1.26/20261028T075350Z-0ed65245`。Windows更新签名验证通过，SHA256 `3e5cae68c0715009bf7553df00913d6d2ae50dd02778d52d74c5f00346e4dd63`；Android正式身份`cn.zijun701.plan.app`／31、固定证书、非debug、禁止明文及登录受限更新源验证通过，SHA256 `60b0df924a623fcc75ca4e88570e00db33de38c3ff8cc908f2751e3f3f004dad`。Windows没有新增Authenticode认证，不把更新签名称作已认证发布者。

首次Android release构建因代理访问官方Maven Central返回403失败，保留已成功Windows产物，仅用既有`-DirectMavenCentral`补Android构建成功，再以正常本机权限核对受限公钥证书及实际字节；没有改镜像源、TLS或签名身份。7项发布身份／签名守卫通过。签名候选尚未用于正式覆盖或公开／受限分发；[机器续做记录](release-0126-resume.json)按实际门槛区分已完成与未完成。
