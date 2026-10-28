# 计划舱0.1.25：更新体验（Web/API已发布，原生分发延期）

## 当前实际交付

2026-10-28续做更新：维护者已重新要求完成阶段5全部范围，独立PC真实更新验收进行中，Android尚未收到本轮准备确认。已验证本机签名检查、忽略重开、必更优先和坏签名拒装；正确包已下载，等待本人确认安装，不提前认定跨版本队列保留通过。专用验收工具和首次凭据隔离问题的完整记录见[最新检查点](update-experience-progress.md)。以下“本轮”发布记录指此前已完成的非真机窗口；正式分发仍未变化。

本轮依据维护者的最新要求，**只完成第五阶段非真机范围**：Web/API已发布0.1.25，Flyway仍V34；Windows官网／可选源和Android登录受限源继续0.1.24／29。0.1.25／Android30正式签名候选保留，但没有安装、覆盖、发布或新增GitHub可下载附件。

维护者将本轮PC和Android真机、正式升级及实际应用内更新验收延期到阶段6开始前。过去的独立包基础验收不能替代这些门槛；只有补齐后才能称第五阶段三端完整交付。公众注册、官网APK／二维码和生产必更均未开放，自动下载默认关闭。

第五阶段源码包括：有界自动检查和失败退避，按更新源／平台／版本及Android账号隔离的忽略／稍后处理，明确的安装版本亮点与历史，以及PWA刷新前确认。原生部分仅在候选中，当前已发布0.1.24客户端不能据此宣称获得全部0.1.25交互。

## 不可变产物

- 已冻结正式候选的共享业务源码提交`031ce0c`，版本统一0.1.25／30；后续增加发布工具、测试、文档及默认不启用的`updater-probe`专用验收特性，不重制正式安装包。该特性隔离本机API和凭据、启动时拒绝非测试身份／无本机TLS，禁止WebView调试；正式候选未包含该特性。
- 正式候选`production-artifacts/client-release/0.1.25/20261027T081918Z-39d6b279`。Windows SHA256 `39a3b923262474e55eb18bcd0f2233668e42903d609e7bc4fd84009269d350fb`；Android `1c748869224eb470b95a528d39e3b626be87cd3369b3091024a9209b80846390`。原身份、Windows更新签名和Android证书已核验，候选未公开。
- API JAR `78dfb43b0a0a2538bb1df4a3bc0ccdb0a8d9a93e8cf09ebaf2313a69738e9e73`；部署归档 `4be0a01f7ebfed29b8559cf97bda5e44c90bfdd53d597a3decc6953ed3ba9e80`，服务器目录`/home/oyts/plan-platform/releases/0.1.25-4be0a01f`。
- 本轮没有修改共享业务源码或重建该API和EXE／APK字节。0.1.25新镜像由原归档在ECS本地构建，不拉取新基础镜像；旧镜像、目录、卷和备份保留。

## 本轮生产与恢复门槛

新快照`plan_platform-mysql-20261028-003057.sql.gz`通过gzip／SHA256及62表两份隔离恢复，恢复库`plan_restore_20261028_003059`及`_reference`。0.1.25候选与0.1.24旧镜像依次在480MiB、0.6CPU、无公网端口的容器中启动；61张原业务表全部字段保持不变，V34→V34没有迁移。核心三表users／task_templates／mainlines使用全字段JSON行哈希，其余表使用完整CHECKSUM；不读取或输出私人正文。

演练证据`release-drills/roadmap-20261028003056-4e53d97c/report.json`。两个测试容器已停止并保留，没有挂载生产AI／SMTP密钥，没有在恢复库上调用模型、发送邮件或开公众注册。

激活前预检通过，新工具目录`update-release-tools/0125-709f50e7`。激活仅改变Web/API版本，保留原安全、AI、邮箱变更、成员治理、采集和私有挂载；新增指标邮件继续关闭。保留`production-telemetry.yml`及原生静态／私有分发叠加文件。失败回退能力包含generation、long-planning、admin-runtime和metrics四项；不能使用缺失这些能力的旧写服务。

升级后新备份`plan_platform-mysql-20261028-004401.sql.gz`经既有ECS角色上传私有OSS并真实下载比对，SHA256 `8c58013444577f9139518efadf627b2e7f4fa09ca26d4ca14fefb29a6c67e3f7`。再恢复62表到`plan_restore_20261028_004404`及`_reference`，全部比较通过后才更新运维恢复证据。保留的61个计划采样最新59秒、健康／日志完整，原cron存在，新指标邮件关闭；这不是61个全新0.1.25样本或生产容量保证。

详细升级后证据`post-release-verification.json`位于新发布目录。备份仍在原受限`releases/0.1.15-50bc92a/backups/`，这不代表生产仍为0.1.15。没有清理旧文件、数据或恢复点。

## 定向验收

- 本轮发布守卫6项、快照守卫5项、运行／备份位置守卫4项通过；没有重跑无关旧业务。
- 真实构建PWA四组通过：Service Worker接管；通过实际离线新增建立1项合成队列；新Worker等待时不强制刷新；取消保留未提交输入；明确确认后由controllerchange重载，队列仍在、无自动业务提交、亮点不重复。使用本机静态构建和合成API，不是原生安装或生产离线写入证明。
- 正式HTTPS五组通过：健康和账号配置、Windows公开清单逐字节不变及Android匿名拒绝；桌面／手机网页分别验证亮点关闭、历史、布局、刷新登录保留、Secure HttpOnly刷新Cookie及Android账号绑定的旧受限清单。未执行APK下载／安装，未修改真实用户计划。
- 前一轮52项前端、28项浏览器、10项API契约、Rust5及Android规则7、独立PC五组／Android三组结果继续保留在[阶段记录](update-experience-progress.md)，不是本轮重新执行或正式升级证明。

本机证据：`acceptance-results/update-experience/pwa/2026-10-28T00-34-41-121Z/`、`acceptance-results/public-web/20261028-004144-a516ae/`和`acceptance-results/update-experience/production/`，均不进入Git。先前测试工具失败保留：PWA运行路径初始化顺序、两个队列链接的唯一定位、刷新Cookie查询了网站根而非`/api/auth`。这些仅修测试工具，未放宽产品安全策略或重制签名包。切换后健康已确认，未宣称零中断。

## 阶段6开始前必须补齐

1. 先重新征得可用时段及保存关闭确认，核对0.1.25不可变候选和实际旧安装版本；不能复用过期窗口句柄或假定手机在线。
2. 用隔离合成账号／数据补PC与Android的有界检查、忽略／稍后持久保留、新版本再次提醒、换账号／源、必更优先及一次性亮点。原安全出口、默认关闭自动下载及签名失败拒装保留。
3. 补真实受影响的应用内下载／安装链路与合成非空队列跨升级保留。固定可信源、身份、签名／哈希和系统确认不变；不能用手动覆盖、桥合约或PWA刷新替代。先规划可安全执行的隔离验收方案，再操作正式客户端。
4. 同身份正式升级只读验收原账号、已有计划／休息日、队列和冷重开；不卸载、不清数据、不提交真实队列。需要密码或系统安装／风险确认交给本人。
5. 通过后再发布Windows官网／可选源、Android登录受限源，做精确哈希／鉴权／缓存和原生检查；最后创建不可变`v0.1.25`和Windows-only GitHub Release。此时才能收口三端交付并开展阶段6核心冻结／综合回归，不提前启动全站UI重构。

## 回退准备（本轮未执行）

自动激活异常已有守卫回退。若完成后需要人工回退，在服务器Linux终端核对当前确为本目录，先运行全部能力守卫，再恢复原应用镜像、核验HTTPS0.1.24，最后才恢复current指针。以下不降级或还原数据库，不删除任何新数据：

```sh
set -eu
root=/home/oyts/plan-platform
release=$root/releases/0.1.25-4be0a01f
previous=$root/releases/0.1.24-a4a0d911
test "$(readlink -f "$root/current")" = "$release"
python3 "$release/scripts/check-governance-rollback.py" --target-supports-generation yes --target-supports-long-planning yes --target-supports-admin-runtime yes --target-supports-metrics yes
sh "$release/scripts/production-compose.sh" "$previous" up -d --no-deps backend web
curl --max-time 15 -fsS https://plan.zijun701.cn/api/health | grep -q '"version":"0.1.24"'
test ! -e "$root/current.new" && test ! -L "$root/current.new"
ln -s "$previous" "$root/current.new"
mv -Tf "$root/current.new" "$root/current"
```

旧镜像启动兼容已在隔离库验证，但本轮没有实际回退现网。原生清单始终0.1.24，因此本轮没有修改分发回退指针。若状态与以上前提不符，停止并核对证据，不盲目重复激活脚本或V33→V34旧演练。
