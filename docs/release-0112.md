# 计划舱 0.1.12 AI 教学与节奏调整

2026 年 10 月 16 日，正式 Web/API、Windows 官网与可选更新源、Android 登录受限更新源均已发布 0.1.12。Android 构建号为 17。两端使用原正式签名，完成保留数据覆盖升级及本版定向验收；官网 APK、二维码和公开注册继续关闭。线上默认 20 次/天，管理员的默认值、个人覆盖与暂停控制保持不变。本轮新增 DeepSeek 付费请求为 0。

## 新增功能

“使用教学”新增 AI 规划课程，可从首次进入 AI 页的提示开始，也可随时回顾或重学。四步依次练习：说明目标并确认模式、预览并亲手修改草稿、勾选确认并点击生成、查看节奏调整对照并确认应用。练习案例明确标为模拟，不连接模型，不写真实计划或离线队列。原四节教学进度保留，旧版 v1 存储只补入新的 AI 课程。

已有对象调整页面在用户同意载入之后，显示原事项数量和历史保护数量，并提供“周期延长一周”“每项缩短到15分钟”等适用示例；路线保持无固定每日安排，待办仅针对所选单项。点击只填写尚未发送的要求，不调用模型、不扣次数、不自动修改对象。替换用户已输入的文字前需要确认；有待同步操作、未保存草稿或 AI 处理进行中时，示例按钮禁用。

延长周期的日期取自所选对象的原结束日期，并加七个日历日；不改变原起点，要求保留休息日和执行历史。无效或超出日期范围时不提供该示例。所有原事项受保护时不提供修改原事项的示例。后端的所有权、版本快照、409 和事务应用规则保持不变，示例不是服务端校验的替代品。

## 定向验证

- 18 项前端单元通过：AI 教学步骤前置条件、旧进度兼容、账户／服务器隔离、教学存储生命周期、节奏示例日期边界和无副作用。
- 22 项浏览器测试通过，覆盖桌面与手机视口：新增 AI 教学、保存重开、确认防误触、原课程兼容，以及受影响的调整预览、409、队列阻断、示例填入和替换确认。教学请求监视未发现业务或 AI 写入。
- 1 项新增后端综合用例通过：两周周期计划延长到第三周，仅未开始任务改期和缩短时长，确认前真实数据不变，确认后原任务 ID、数量及完成记录保留，重复应用不增行。使用 H2 隔离测试，不调用模型。
- Web、Windows 与 Android 构建通过；原 Windows 更新签名及 Android 正式证书、包名、构建号、禁用调试与严格 HTTPS 均核对通过。
- 正式 HTTPS 网页、独立 Windows Rust 客户端、Android 真机 Java 客户端各通过 2 个新增场景：四步教学、保存重开、重学，以及只填入节奏要求、替换未发文字前确认、真实任务版本不变。使用受邀合成普通账号，教学前后计划列表和会话不变；节奏场景只创建明确标注的合成测试计划，未应用调整，未扣次数。
- 正式 Windows 原目录 `/UPDATE` 升级前备份原程序与应用数据，启动前 411 个已有数据文件逐项哈希不变；原账号、云端地址、AI 教学入口、0/0 队列、关闭重开保留登录，以及发布后更新检查通过。自动下载未勾选。
- 正式 Android 从 0.1.11/16 同包名同证书覆盖到 0.1.12/17，原账号仍登录，AI 教学入口正常，检查前后队列均为 0/0。发布后正式手机检查更新显示最新，自动下载未勾选。没有强制终止正式手机应用；重载与教学进度恢复在独立包中验证。本轮不声称非空队列跨升级或全部历史版本的应用内下载安装重新验收。
- 官网 HTTPS、EXE 双地址哈希及真实签名、缓存、桌面／手机视口下载与关闭 APK 二维码共 5 项通过。受限 Android 清单与同一哈希 APK的未登录拒绝、公共地址关闭、两个普通账号身份绑定及私有不缓存共 4 项通过。

测试范围固定在 `scripts/release-test-scope.json`，没有重跑无关业务全量回归。截图在 `web/test-results` 中的 `ai-learning-adjustment.png` 与 `ai-pacing-guide.png`，后端报告为 `backend/target/surefire-reports/TEST-com.oyts.planplatform.ai.AiAdjustmentIntegrationTest.xml`，均不上传为公共附件。

## 安装包和更新

正式候选目录为 `production-artifacts/client-release/0.1.12/20261016T070746Z-95b29d4e`，发布的文件与本机验收包逐字节相同：

- Windows：`windows/Plan Cabin_0.1.12_x64-setup.exe`，3485131 字节，SHA256 `565476b844cc7421714b0673d16aa9c48889deade240159b3126ba1addf87771`。官网提供下载，正式旧版可检查可选更新；长期更新签名不是 Authenticode 发布者认证。
- Android：`android/PlanCabin-0.1.12-release.apk`，3386337 字节，构建 17，SHA256 `39eef78f1d00393df824e8dd08e244e649aa109ee91a6deda85e686289d75433`。仅私下提供或登录受限更新，不上传 GitHub 公共附件。

已接入受限 API 的正式 Android 旧客户端登录后可检查更新。尚未接入的 0.1.4/0.1.5，或不同包名／签名的旧测试客户端，按 [受邀测试说明](invited-client-testing.md) 手动迁移；不重开已到期的匿名窗口，不降低签名或 TLS 校验。安装者自行处理系统安装确认，不关闭安全防护。

朋友首次使用先由管理员创建各自的普通成员邀请，使用本人账号注册登录。三端服务器地址均为 `https://plan.zijun701.cn`；进入“使用教学”可先练习，AI 教学不扣次数。邀请链接只发给本人，不与安装包一起公开发布。

## 生产发布与回滚

业务部署提交为 `561c16635f1b`，目录 `/home/oyts/plan-platform/releases/0.1.12-561c16635f1b`。部署门禁修正了遗漏的 API 健康版本配置，Web、API、Windows 和 Android 元数据一致；没有更改后端业务协议或数据库迁移。本轮仅升级应用镜像，保留旧 Node 服务、历史发布、镜像和数据库卷。

升级前 MySQL 备份为本版目录的 `backups/plan_platform-mysql-20261016-072046.sql.gz`，gzip 和 SHA256 校验通过，已使用原 ECS 角色上传私有 OSS及校验文件。当前生产指针指向新版，HTTPS 健康、登录与新增业务通过。AI 密钥原文件权限和非 root 可读性保持，只读挂载 `read-write=false`，未读取密钥值；原时区、静态下载和受限 Android 覆盖均保留。

应用回滚在服务器 SSH 终端执行，不恢复或降级数据库：

```sh
set -eu
root=/home/oyts/plan-platform
previous=$root/releases/0.1.11-9f92b37e95c9
test "$(cat "$root/releases/0.1.12-561c16635f1b/previous-release")" = "$previous"
sh "$root/current/scripts/production-compose.sh" "$previous" up -d
for attempt in $(seq 1 45); do
  curl -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.11"' && break
  sleep 2
done
curl -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.11"'
test ! -e "$root/current.rollback-0112"
ln -s "$previous" "$root/current.rollback-0112"
mv -T "$root/current.rollback-0112" "$root/current"
```

Windows 静态入口独立回滚为 `sh /home/oyts/plan-platform/client-public/releases/windows-0112-K252pk/publish.sh rollback windows-0112-K252pk`；Android 受限清单独立回滚为 `sh /home/oyts/plan-platform/android-private/staging/android-17-03626e01/rollback.sh`。它们保留已上传文件，不强制降级已安装客户端或删除业务数据。

## 验收证据与复跑

以下本机证据由 Git 忽略，不上传账号、原始日志或截图：

- Windows 新功能：`acceptance-results/ai-native-web/2026-10-16T07-15-08-916Z-aeb45d/report.json`。
- Android 真机新功能：`acceptance-results/ai-native-web/2026-10-16T07-16-06-474Z-ef7fd3/report.json`。
- 公网新版新功能：`acceptance-results/ai-native-web/2026-10-16T07-23-24-407Z-5eee9e/report.json`。
- Windows 安装数据比对：`.desktop-tools/formal-windows/private/20261016T071014Z-3fa3ce63/installation.json`；正式窗口通过电脑操作工具检查，原账号数据未用于写入测试。
- 官网下载：`acceptance-results/windows-public-cloud/2026-10-16T07-24-05-651Z-81a7993c/report.json`。
- Android 鉴权更新：`acceptance-results/invited-android/2026-10-16T07-25-22-825Z-1fb7bb1d/report.json`。
- 正式手机更新检查：`acceptance-results/android-ai-formal/20261016T072635Z/report.json`。

失败的中间报告保留。原生工作区启动会回到首页，测试需重新打开已保存课程；复跑从中间进度开始时，先经界面返回第一步。公网脚本等待课程渲染后再选择操作，避免把加载中的控件当作当前步骤。以上均为测试驱动调整，没有以重置用户进度或更改正式包来掩盖问题。

```sh
node scripts/ai-native-web-acceptance.mjs --web-only --learning-pacing-only
node scripts/ai-native-web-acceptance.mjs --windows-only --learning-pacing-only
node scripts/ai-native-web-acceptance.mjs --android-only --serial DEVICE_SERIAL --learning-pacing-only
python scripts/android-learning-entry-acceptance.py --serial DEVICE_SERIAL --expected-version 0.1.12 --expected-code 17 --ai-only
python scripts/android-ai-formal-acceptance.py --serial DEVICE_SERIAL --expected-version 0.1.12 --expected-code 17 --update-only
```

原生命令要求先构建当前独立回归包，并在手机确认安装。正式 UI 只读检查不能与独立手机测试并行争用前台。本版不变更模型供应商、调用协议、密钥、数据库结构或用户注册策略。付费 AI 仍不离线自动重放，既有付费验收标记不删除、不重跑。
