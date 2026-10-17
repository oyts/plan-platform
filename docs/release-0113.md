# 计划舱 0.1.13 账号恢复与 AI 运行管理

本版完成账号恢复与邀请闭环、朋友试用时的 AI 状态引导，以及管理员用量面板。2026 年 10 月 17 日正式 Web/API 已升级到 0.1.13，公网定向检查通过。SMTP 尚未配置，找回密码仍关闭。Windows 已在原目录覆盖到 0.1.13，原登录保留，尚待完成正式界面检查；Windows 和 Android 更新源继续保留 0.1.12。Android 0.1.13 构建号 18 待 USB 真机验收后发布。

## 账号恢复与邀请

找回密码增加二次输入、密码规则说明、重发冷却和失效链接重新申请入口。服务器对已知和未知邮箱返回同样的反馈，限制同邮箱和同来源发送频率，接口不透露账号是否存在。重置链接加事务锁，只能成功消费一次，成功后撤销旧刷新会话。已经签发的短时访问令牌仍按原有效期到期，不宣称立即撤销所有访问令牌。

管理员可对过期、撤销或遗失链接的邮箱重新邀请。新邀请创建成功后旧有效链接失效；创建失败不撤销旧链接。已经注册的账号使用找回密码，不重复创建账号。没有 SMTP 时仍可通过原有一次性邀请链接邀请朋友，公开注册保持关闭。

成员页新增邮件状态面板，只有管理员可查看和发测试邮件，收件人固定为当前管理员账号邮箱。测试成功仅表示 SMTP 接受，必须由本人检查实际收件箱和垃圾箱。授权码不通过网页输入，不进入客户端、仓库、聊天或日志。

## AI 试用引导

AI 页分别说明排队、生成中、额度不足、暂停、失败和结果待核实的处理方法。明确拒绝的请求保留输入，可在条件恢复后手动重发；断连或网关异常造成的未知结果要求先刷新核对，不自动付费重试或退款。轮询失败逐步退避，手动刷新同时重新读取服务能力。

未发送文字和未保存草稿均有离开确认；教学、手动规划、外部 AI 导入仍可独立使用。新增下一步提示和安全反馈建议，不要求朋友提交密码、完整邀请链接或私人对话。没有改变模型、每日默认 20 次及管理员热修改规则。

## 管理员用量

新增私有、不缓存的 `/api/admin/ai/usage`，按北京时间汇总最近日期范围内的成功、失败、待核实、排队和生成中请求，提供已知输入输出 token、耗时、受限分页与当前并发和熔断状态。普通用户不可访问。

V18 仅为额度账本添加可空运行元数据和索引，不存对话、草稿或私人计划。新记录删除对话后仍保留到原清理周期；旧记录无法确定 token 时标为未知，不作为零费用或服务商账单。面板没有补扣、退款、再次调用或修改用户业务数据的操作。

## 定向验收

- 后端 22 项通过：账号恢复 6 项、用量 4 项、受影响对话 11 项、额度热更新 1 项。包含同链接并发只消费一次、刷新会话撤销、邀请旋转事务、管理员权限、元数据不泄漏和删除会话后统计保留。H2 迁移 V1 至 V18 通过。
- 前端单元 11 项通过；相关浏览器 24 项通过，覆盖桌面和手机视口。布局防溢出和旧服务器响应保护最终修订后的 10 项新增场景也通过。这些手机视口结果不是 Android 真机结果。
- 独立 Windows 原生客户端通过 3 项：真实 Rust 传输下的管理员面板与合成 SMTP、排队状态和未发送内容保护、真实接口重置密码与一次性链接。证据：`acceptance-results/account-ai-native/2026-10-16T22-46-43-249Z-c019cd/report.json`。使用本机内存数据库和合成邮箱，不修改正式客户端资料。
- 发布防护脚本 4 项通过，版本一致性、原更新签名和 Android 证书验证通过。部署在已知 AI 排队／生成中时拒绝重启，不把新版本环境变量带入旧版本回退。测试范围见 `scripts/release-test-scope.json`。
- 本轮 DeepSeek 付费调用 0 次，真实外部邮件 0 封。
- 生产快照在独立 MySQL 8.4 恢复库完成 V18 迁移和原 0.1.12 镜像回退；未变化表校验相同，额度账本按原列投影逐项比对相同。新增可空列导致表结构变化，不冒称整张新表与旧表的 CHECKSUM TABLE 相同。恢复库及停止的演练容器保留，未挂载密钥或改写生产数据。
- 独立 MySQL 候选 3 组检查通过：元数据分组及分页、缺失 token 和隐私边界，邀请换新后旧链接拒绝，以及普通成员无法读取邮件／用量／额度管理。全部使用合成账号和元数据，不调用模型。
- 正式 HTTPS 网页桌面和手机视口 2 组通过：实际登录、AI 帮助及未发送文字保护、普通账号访问管理接口为 403、私有不缓存、未配置 SMTP 不伪装为开放。前后额度不变。证据：`acceptance-results/account-ai-public/2026-10-16T22-55-08-042Z-b25fb3/report.json`。

重跑定向原生场景：先构建当前 `scripts/desktop.ps1 -Action regression` 与隔离 API，再运行 `node scripts/account-ai-native-acceptance.mjs`。脚本只启动并关闭自身测试进程，不卸载或清理正式应用。

## 候选安装包

目录：`production-artifacts/client-release/0.1.13/20261016T224235Z-d87422e1`。

- Windows：`windows/Plan Cabin_0.1.13_x64-setup.exe`，3488144 字节，SHA256 `0c83ee0a19753b06030b8d7f34f17fa69913f364fd8b89161e71d65606ffebf3`。由原长期更新密钥签名，尚未发布到官网或更新源。
- Android：`android/PlanCabin-0.1.13-release.apk`，构建 18，SHA256 `a5fbf315047f5e5dfbd1c82a839f9de60c73cfec4fa61198b94b0b9178614ded`。保留原包名、正式证书，不公开上传；待 USB 真机验收后才能提供给朋友或推送受限更新。

## 邮件启用步骤

先由管理员在自己的 QQ 邮箱中开启 SMTP 并生成专用授权码。不要使用 QQ 登录密码，也不要把授权码发到聊天或保存到公开配置。

以下命令在 FinalShell 已连接的服务器终端执行，不在 Windows PowerShell 中执行。候选脚本只创建受限文件，不会立即开放找回密码。

```sh
cd /home/oyts/plan-platform/current
sudo sh scripts/configure-mail-secret.sh
sudo sh scripts/prepare-mail-overlay.sh
sudo sh scripts/activate-mail.sh test
```

在成员页刷新邮件状态，点击“发送管理员测试邮件”，本人确认实际收到后，再执行：

```sh
sudo sh scripts/activate-mail.sh enable-reset --received-test-email
```

最后用自己的邮箱验证一次找回密码。自动化不会替管理员确认收件。邮件配置异常时脚本恢复原邮件配置，数据库和受限密钥文件保留；已有 SMTP 文件不自动覆盖。邀请制和公开注册状态不变。

## 正式 Windows 覆盖升级

用户确认保存并关闭客户端后，已使用同一正式候选在 `E:/PlannedCabin/Plan Cabin` 从 0.1.12 覆盖升级至 0.1.13。安装前核对原长期签名和候选 SHA256，备份后使用 `/UPDATE`，没有卸载或清理数据。启动前 419 个已有应用数据文件逐项哈希一致；未导出或修改系统凭据。安装记录保存在 `.desktop-tools/formal-windows/private/20261017T062859Z-1b649c86/installation.json`。

启动后可读取到原账号已登录、今日待办页面和原工作模式。新增管理员面板、同步队列、客户端版本页及关闭重开检查尚未完成：电脑操作工具先返回 `coordinate input geometry is unavailable`，重新选择窗口后返回 `failed to activate captured window`，已停止输入，等待用户将正式窗口置前。此为自动化窗口操作受阻，不据此判断产品业务故障，也不计作界面验收通过。

## 生产升级和回退

业务部署目录为 `/home/oyts/plan-platform/releases/0.1.13-932e951cce35`，当前指针已切换。升级前备份 `backups/plan_platform-mysql-20261016-225411.sql.gz` 及 SHA256 校验文件已通过检查，并使用原 ECS 角色上传私有 OSS。生产迁移版本为 18，默认额度仍为 20，AI 密钥保持原 100:101、0600、只读挂载，值未被读取。

上一版本 `/home/oyts/plan-platform/releases/0.1.12-561c16635f1b`、旧镜像、MySQL 卷和旧 Node 服务均保留。需要回退时，在新的服务器终端执行以下应用镜像回退，不导入旧备份覆盖新数据库：

```sh
unset PLAN_VERSION
sh /home/oyts/plan-platform/releases/0.1.13-932e951cce35/scripts/production-compose.sh /home/oyts/plan-platform/releases/0.1.12-561c16635f1b up -d
curl -fsS https://plan.zijun701.cn/api/health
```

确认健康版本恢复到 0.1.12 后再同步部署指针：

```sh
test ! -e /home/oyts/plan-platform/current.rollback-0113
ln -s /home/oyts/plan-platform/releases/0.1.12-561c16635f1b /home/oyts/plan-platform/current.rollback-0113
mv -Tf /home/oyts/plan-platform/current.rollback-0113 /home/oyts/plan-platform/current
```

回退前也应确认没有正在处理的付费 AI 请求。V18 是向后兼容的新增可空列，旧镜像已在恢复库实际启动验证；不删除新增元数据。

## 剩余发布门槛

正式 Windows 待窗口置前后继续完成新增面板、队列、版本及关闭重开检查，再推进官网与可选更新源发布。无需重复覆盖安装。真实 SMTP 收发等待管理员配置；Android 真机等待用户连接手机。未通过这些门槛的客户端渠道不发布，原 0.1.12 下载和受限更新源保留。
