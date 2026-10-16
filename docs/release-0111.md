# 计划舱 0.1.11 AI 规划发布记录

Web 和 API 已在 `https://plan.zijun701.cn` 升级到 0.1.11，AI 已启用，当前线上默认每日额度为 20 次。Windows 正式签名包已完成保留数据覆盖安装，官网与可选更新源发布 0.1.11。Android 同身份正式包为 0.1.11、构建 16，2026 年 10 月 16 日已完成 USB 真机定向验收并发布到登录受限更新源；官网 APK、二维码和公开注册保持关闭。

## 本版功能

- 内置官方 DeepSeek 对话：先了解需求、推荐模式，用户选择周期计划、目标路线或今日待办后生成草稿。
- 草稿可多轮讨论、手工修改、保存和恢复；只有明确确认后才原子生成业务数据。外部 AI JSON 导入作为免费降级入口。
- 已有对象可从具体业务页进入 AI 调整。先同意载入所选内容，逐项对照原内容和建议内容，保留所有原事项、执行历史、计时与未修改字段；支持修改未开始事项并追加新事项。不会删除后重建旧计划。
- 调用前和应用前检查所有权及对象、事项、进度、计时、阶段和依赖快照；过期返回 409，保留草稿。批量失败整批回滚，重复确认不重复写入；本机离线队列未处理时不能应用调整。
- 管理员可热调整默认和个人每日次数、恢复继承或暂停成员。每日次数以北京时间自然日统计；明确失败退回预占，未知结果不自动退款或重试。
- AI 不可用、无额度或离线时，手动业务与已保存草稿仍可用；付费请求不会进入离线自动重放。

AI 仅自动附带所选对象的必要内容和当前对话，不扫描全账号计划，不附带密码、账号标识、执行备注或服务器 API Key。用户自己输入的对话也会发送给第三方，请勿自行粘贴敏感资料；这不是自动脱敏服务。单条消息最多 8000 字符，默认上下文 16000 字符、输出 4096 token，超限明确拒绝而不是截断原计划。详细边界见 [AI 对话说明](ai-conversations.md)。

## Windows 恢复问题与版本

0.1.10 验收发现原生客户端在认证恢复前先挂载路由，重载 AI 草稿时会被导回业务首页。0.1.11 只保留经过校验的 AI 会话或目标链接，在真实认证恢复后再导航；不会绕过登录、必更限制或离线队列检查。0.1.10 服务和候选包保留，不用同版本不同字节覆盖已部署镜像。

正式 Windows 经 0.1.9 → 0.1.10 候选 → 0.1.11 保留数据升级。两次安装均在原目录运行 `/UPDATE`，启动前 404 个原应用数据文件校验和不变，未导出或修改系统凭据。正式窗口确认原账号仍登录、云端地址正确、AI 入口可达、安装版本 0.1.11、自动下载未勾选。当前正式队列为 0 项、0 冲突，不能将空队列检查说成非空队列升级验收。

## 定向验收

本版只验证 AI 新业务及直接受影响范围，清单为 `scripts/release-test-scope.json`。

| 范围 | 结果 |
| --- | --- |
| 后端 AI、额度、官方协议假上游、原业务幂等单例 | 40 项通过 |
| 前端 AI 协议、差异、传输、原生会话恢复 | 11 项通过 |
| 桌面和手机网页布局、对话、额度、调整与冲突 | 20 项通过 |
| 正式 HTTPS Web 与独立 Windows Rust 客户端 | 8 项通过，合成普通账号，0 次付费调用 |
| MySQL 8.4 V17、三模式调整、历史保护、所有权、409、重放、整批回滚 | 独立候选通过 |
| 新增 DeepSeek 官方调用 | 3 次均成功，仅合成内容，提交前记账，无自动重试；原有 5 次新建规划验收未重跑 |
| 生产备份恢复、V13 → V17、旧镜像回退 | 独立恢复库通过，全部原有业务表校验和不变 |
| Android 正式身份、构建号、签名、内置受限更新地址 | 通过；同哈希 APK 已完成保留登录覆盖安装、重开和更新检查 |
| Android 真机 AI 定向业务 | 独立测试包真实 Java HTTPS 7 项通过，正式包只读 6 项及发布后更新检查 1 项通过 |

8 项真实云端界面测试包括三种模式修改原事项和追加新事项、差异预览、保存、重载、确认写入，以及零额度时的免费手动路径。Windows 使用独立回归身份与真实 Rust 请求桥，不修改正式应用数据；测试账号通过真实受邀账号 API 认证，不自动填写用户登录窗口。

本机证据为 `acceptance-results/ai-native-web/2026-10-15T20-34-29-491Z-536c41/report.json` 及同目录截图，安装前数据比对为 `.desktop-tools/formal-windows/private/20261015T203354Z-861cf3bc/installation.json`。它们被 Git 忽略，不作为公开附件上传。失败的中间验收记录保留，以上结果指修复后的成功轮次。

## 服务器和恢复

业务源码部署提交为 `9f92b37`，目录 `/home/oyts/plan-platform/releases/0.1.11-9f92b37e95c9`，回环端口 43140。旧 Node 43127、历史发布目录、镜像、数据库卷和备份均保留。两组 AI 候选及迁移演练容器已停止，数据库和私有证据保留，避免占用 2 GB ECS 内存。

AI 密钥继续使用用户配置的受限文件，只读挂载到非 root 后端；没有把密钥值放进 Compose、环境变量、源码或客户端。生产 `production-ai.yml` 将并发限制为 1、排队 2。首次部署时默认用户额度为 0；维护者随后明确要求线上默认值改为 20 次/天，已通过管理员页面热保存。管理员仍可给个人设置覆盖值或暂停，保存后下一次请求立即生效。

0.1.10 升级前生产备份为该版本目录的 `backups/plan_platform-mysql-20261015-202500.sql.gz`；0.1.11 升级前备份为本版本目录的 `backups/plan_platform-mysql-20261015-203333.sql.gz`。两份均校验并上传私有 OSS。新迁移只新增 AI 表或字段，不删除旧业务表。

应用回退到上一套已验证的 0.1.10 时，在服务器终端执行以下命令；不恢复或降级数据库：

```sh
cd /home/oyts/plan-platform/releases/0.1.11-9f92b37e95c9
test "$(cat previous-release)" = /home/oyts/plan-platform/releases/0.1.10-3e3e3c8b04a2
sh scripts/production-compose.sh /home/oyts/plan-platform/releases/0.1.10-3e3e3c8b04a2 up -d
for attempt in $(seq 1 45); do
  curl -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.10"' && break
  sleep 2
done
curl -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.10"' || exit 1
test ! -e /home/oyts/plan-platform/current.rollback-0111
ln -s /home/oyts/plan-platform/releases/0.1.10-3e3e3c8b04a2 /home/oyts/plan-platform/current.rollback-0111
mv -T /home/oyts/plan-platform/current.rollback-0111 /home/oyts/plan-platform/current
```

只有健康返回 0.1.10 才更新 current 指针；如健康失败，保留数据库和发布目录排查，不删除数据或反复初始化。官网下载独立回退命令为 `sh /home/oyts/plan-platform/client-public/releases/windows-0111-z1AgUk/publish.sh rollback windows-0111-z1AgUk`。旧版本下载及签名逐字节保留。

## 安装包

正式候选目录为 `production-artifacts/client-release/0.1.11/20261015T203153Z-fcb5a6c1`。

- Windows：`windows/Plan Cabin_0.1.11_x64-setup.exe`，3485846 字节，SHA256 `a8c37532532db6fbb41b77848fdac3d10a854559927e6ef1d9d67d5dddeb1b10`。
- Android：`android/PlanCabin-0.1.11-release.apk`，构建 16，SHA256 `e2782154be61b1441676be79846e972d9114d2885cf976a7f1d512405fa02e71`。真机验收后同一文件已发布到登录受限更新源，可供受邀测试；不上传 GitHub 公共附件，不开放官网 APK。
- Windows 使用保留的更新公钥验签；APK 包名仍为 `cn.zijun701.plan.app`，使用原正式证书，不能用旧测试身份或调试包替代。

## Android 真机验收与受限发布

2026 年 10 月 16 日使用已授权的 vivo V2426A、Android 15 完成本次新增内容的验收，没有重跑旧业务全量回归。

正式 APK 从 0.1.9、构建 14 通过 `adb install -r` 覆盖到 0.1.11、构建 16；同包名同证书，不卸载、不清数据。原账号自动恢复，三种模式无连接错误，AI 入口与隐私说明可见，进程关闭重开后仍登录。同步队列升级前后均为 0 项、0 冲突；本次没有验证非空队列跨升级，不将空队列等同于该项验收，也没有改写原计划、休息日或同步队列。

独立回归包与正式包并存，使用同版业务源码、真实 Java 请求桥和云端受邀合成普通账号。7 项通过包括三模式的所选对象同意载入、差异预览、手改和追加、保存重载、确认应用；三模式的免费外部草稿导入、手改、重开和确认生成；以及零额度下禁止付费请求、保留手动降级。云端接口逐项核对生成与修改结果，手机横向溢出不超过 1 像素。没有改真实账号业务数据，未新增 DeepSeek 付费调用。断网与历史保护的既有后端／浏览器证据沿用，本轮未在手机上重复其全部场景。

通过后将同一哈希 APK 发布到登录受限源。两个普通受邀账号均通过清单身份绑定、APK 大小／哈希和私有不缓存检查；未登录或无效凭据均不能获取清单和 APK，公共 APK／旧静态源保持 404，官网清单 `android=null`，公开注册关闭。正式手机再点击检查更新，显示当前最新版本，自动下载仍未勾选。本次是覆盖安装加更新检查，不称为从旧版应用内下载安装的重测。

本机证据（均被 Git 忽略，不上传账号、图片或日志）为：

- 调整及零额度：`acceptance-results/ai-native-web/2026-10-16T06-22-42-121Z-3d3100/report.json`。
- 新建导入：`acceptance-results/ai-native-web/2026-10-16T06-27-47-040Z-f7e586/report.json`。
- 正式包只读与重开：`acceptance-results/android-ai-formal/20261016T062952Z/report.json`。
- 鉴权更新源：`acceptance-results/invited-android/2026-10-16T06-31-35-254Z-021bc132/report.json`。
- 正式包更新检查：`acceptance-results/android-ai-formal/20261016T063252Z/report.json`。

失败的脚本定位、WebView 连接及网络中间记录保留，以上是修正测试驱动后的成功轮次，产品代码与正式 APK 字节未因此更改。新增定向驱动支持以下命令（先构建并安装当前独立回归 APK；`--serial` 替换为实际授权设备）：

```powershell
node scripts/ai-native-web-acceptance.mjs --android-only --serial DEVICE_SERIAL
node scripts/ai-native-web-acceptance.mjs --android-only --creation-only --serial DEVICE_SERIAL
python scripts/android-ai-formal-acceptance.py --serial DEVICE_SERIAL --expected-version 0.1.11 --expected-code 16
python scripts/android-ai-formal-acceptance.py --serial DEVICE_SERIAL --expected-version 0.1.11 --expected-code 16 --update-only
```

受限源回滚只恢复前一清单和挂载配置，不降级手机或数据库：在服务器执行 `sh /home/oyts/plan-platform/android-private/staging/android-16-4de73032/rollback.sh`。已发布的 Windows GitHub 附件及 `v0.1.11` 标签不重写，Android 验收记录作为后续提交推送。

## 默认 20 次与三端真实调用

2026 年 10 月 16 日，维护者要求把当前生产默认每日对话次数从 0 改为 20，并保留管理员修改能力。已在正式 Android 管理员页面保存默认额度，页面返回“对下一次请求生效；当天用量未重置”。普通合成账号实际读取到 `dailyLimit=20`、`overrideLimit=null`、`paused=false`。这是持久化服务端配置修改，没有重启服务、重签安装包或覆盖个人额度；新环境的安全初始化仍为 0，已执行的 Flyway 迁移不改动。

真实请求严格按提交前持久记账的全程 5 次上限执行，不删除标记、不自动重试、不自动补测失败调用。网页、独立 Windows 正式业务源码测试包和独立 Android 真机测试包共用同一受邀合成普通账号，没有把管理员真实计划发送给模型。

| 端 | 真实请求 | 验收结果 |
| --- | --- | --- |
| Web | 1 次周期计划生成 | 2 条任务、星期日休息且不排任务；草稿重开与确认创建通过 |
| Android 15 真机 | 1 次模式推荐、1 次路线生成 | 推荐目标路线；用户选择后生成 2 阶段 3 步骤；草稿重开与确认创建通过 |
| Windows Rust 客户端 | 1 次待办生成、1 次对话修订 | 2 条待办及“晚饭前完成”备注修改；草稿重开与确认创建通过 |

收费请求通过真实浏览器 API／Java／Rust 请求桥提交，不模拟上游，不自动点击 Windows 付费按钮；界面读取实际模型回复和草稿，只有勾选确认并点击生成后才创建真实业务记录。5 条任务均成功，普通账号用量从 0 增至 5，占用为 0，剩余 15。新的网页登录会话逐一核对三端创建的计划、路线和待办均可见，普通成员访问管理员额度接口仍为 403。本轮只覆盖上述真实调用，不把它等同于完整业务回归或所有故障分支重测。

证据均为被 Git 忽略的本机文件，不上传真实回复、账号、凭据或请求原文：

- Web：`acceptance-results/ai-native-web/2026-10-16T06-42-11-694Z-1c1f1c/report.json`。
- Android：`acceptance-results/ai-native-web/2026-10-16T06-42-51-636Z-234ed8/report.json`。
- Windows：`acceptance-results/ai-native-web/2026-10-16T06-44-08-985Z-e31cbd/report.json`。
- 全程付费提交标记：`acceptance-results/ai-official-three-clients/20261016-default20/paid-budget.json`，5 次上限已用完，不能再次运行同一批付费脚本。
- 免费跨端只读复核：该标记目录的 `read-only-2026-10-16T06-45-46-336Z/report.json`。

最初 Web／Android 报告的通用 `scope` 文字仍写着免费测试；其 `official.paidCalls` 从生成时即分别正确记录 1／2 次，独立计费标记也在提交前写入。保留原报告，新增说明并修正后续驱动的报告模板，不通过重跑收费请求更正元数据。
