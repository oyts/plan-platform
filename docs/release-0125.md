# 计划舱0.1.25：更新体验（Web／Windows已发布，Android待真机）

## 当前实际交付

2026-10-28最新：**Web/API及Windows官网／可选源已发布0.1.25，Android正式及登录受限源仍0.1.24／29**。Windows实际应用内更新、合成非空队列、正式覆盖／冷重开和发布后更新检查已通过；Android本轮由维护者选择稍后测试，没有枚举或操作手机。Android 0.1.25／30正式签名候选保留，GitHub不可变v0.1.25归档等待该门槛，当前最新标签仍v0.1.24。

第五阶段包括有界自动检查与失败退避、按更新源／平台／版本及Android账号隔离的忽略／稍后、安装版本亮点／历史，以及PWA刷新前确认。不能把Windows或手机网页结果代替Android实际更新／升级证据。公众注册、官网APK／二维码和生产必更均未开放，自动下载默认关闭。首次独立Windows试包凭据隔离失误及修正经过保留在[阶段检查点](update-experience-progress.md)。

## 不可变产物

- 已冻结正式候选的共享业务源码提交`031ce0c`，版本统一0.1.25／30；后续增加发布工具、测试、文档及默认不启用的`updater-probe`专用验收特性，不重制正式安装包。该特性隔离本机API和凭据、启动时拒绝非测试身份／无本机TLS，禁止WebView调试；正式候选未包含该特性。
- 正式候选`production-artifacts/client-release/0.1.25/20261027T081918Z-39d6b279`。Windows SHA256 `39a3b923262474e55eb18bcd0f2233668e42903d609e7bc4fd84009269d350fb`；Android `1c748869224eb470b95a528d39e3b626be87cd3369b3091024a9209b80846390`。原身份、Windows更新签名和Android证书已核验；Windows精确字节已公开，APK未分发。
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

## Windows实际更新与正式分发（已完成）

独立身份`cn.zijun701.plan.desktop.updateprobe`的本机TLS、测试签名及凭据隔离检查通过。本人确认后实际在应用内安装0.1.24→0.1.25；原合成会话、离线待办及1项操作／0冲突／0次尝试跨升级、冷重开保留，六组事实见`acceptance-results/update-experience/pc-lab/2026-10-28T03-01-02-055Z/ui-observations.json`。试包使用当前共享UI，不是旧正式0.1.24业务字节；不能据此声称正式账号非空队列已测。

本窗口再补三组：忽略25不屏蔽真实签名测试版本26；稍后处理冷重开保留；503失败后立即冷重开没有额外清单请求（3→3），不转必更且原1项合成队列未自动提交。26只构建、验证版本和签名，未安装／发布。证据`acceptance-results/update-experience/pc-lab/2026-10-28T05-43-49-379Z/edge-ui-observations.json`。真实窗口操作使用computer-use，无Windows CDP或自动认证。

正式Windows已在本人保存完全退出后覆盖0.1.25，启动前629个原数据文件不变；原账号、已有日程计划、同步0/0、冷重开及关闭后亮点不重复已核对。受限备份`.desktop-tools/formal-windows/private/20261028T032030Z-80913732`不公开上传。

加密签名备份及云端下载副本哈希重新比对通过，没有读取口令或更改签名。最终Windows准备目录`production-artifacts/windows-public-release/0.1.25/20261028T054829537Z-gAtUdc`，正式更新说明见`client-release-0125-notes.txt`。静态分发`windows-0125-QG4pVi`归档SHA256 `e5dd7e6d751bedf8bf5ee2b5c98b27a8e0afaf5dda2d6256a957d0a802d32915`，上传后核对文件清单／字节并成功激活。仅重建Web静态容器，保留旧安装包、回退指针、私有APK和`plan_private_metrics`脱敏日志采集，不再次迁移数据库。之前准备但未上传的目录保留，不作正式证据。

公网五组通过：健康、注册关闭及HTTPS跳转；no-store可选清单；两条Windows下载路径实际EXE、签名与哈希；桌面／手机网页官网按钮；Android公开下载／二维码仍关闭。证据`acceptance-results/windows-public-cloud/2026-10-28T05-49-33-094Z-2b204ecd/report.json`。正式Windows随后实际点击检查，显示最新0.1.25，自动下载未勾选；之后停止PC输入，电脑交还本人。

## Android验收准备（不是手机通过）

新建隔离调试身份`cn.zijun701.plan.mobile.updateprobe`，名称“计划舱更新验收”，不覆盖正式版或旧回归包。0.1.25／30起点与0.1.26／31目标同测试证书，位于`client-update-artifacts/local/stage5-android/{bootstrap,target}`；真实aapt／apksigner及字节核对通过。**两包都是本机测试包，不得分发朋友、上传官网或受限正式源。** 正式0.1.25／30候选仍为上文冻结字节。

测试构建要求`PLAN_NATIVE_REGRESSION=1`、显式版本、固定loopback HTTPS和测试CA；正式release及非隔离身份拒绝。CA仅用于测试更新器，不修改系统或业务HTTP信任。4项负向构建／身份保护、7项既有Android更新规则及3项本机真实TLS／APK字节／路径保护通过；52项受影响前端用例随两包构建重复通过，不累计为104项。Windows实验室6项TLS补验通过；先前因合法新增Android路径而失效的404断言已改为未知路径，旧失败证据保留。首次Gradle缓存权限失败后，以正常本机权限重试构建成功，没有清缓存或放宽ACL。

元数据`client-update-artifacts/local/stage5-android/metadata.json`；TLS证据`acceptance-results/update-experience/android-probe-preflight/1793167106118.json`；构建守卫`test-android-update-probe-guards.mjs`。这只验证本机传输与构建，不是Android真机、系统安装或正式登录受限鉴权证明。本机测试源有意不要求登录，正式通道仍必须登录。没有枚举、安装或控制手机。

## 剩余收口（阶段6尚未开始）

1. 等维护者确认手机可用后，先枚举唯一实际设备，用隔离身份补本阶段更新提示／持久选择、必更优先／坏哈希拒装、重开及真实下载→本人系统确认安装→合成非空队列保留。锁屏／其他应用前台／系统安全提示立即交还本人，不自动绕过。
2. Android正式覆盖须再次保存确认；核对实际旧版本、同包名／证书／冻结APK、原登录与队列，升级后只读检查原计划、冷重开。不能用手动覆盖替代上一步应用内链路，不提交真实队列。
3. 通过后发布Android登录受限可选源，补鉴权、账号隔离、哈希／缓存与正式手机实际检查。官网APK／二维码及公众注册继续关闭。
4. 最后创建不可变`v0.1.25`和Windows-only GitHub Release，不附APK／测试资料；之后才可称阶段5完整交付、进入阶段6。无需重装已验收PC或重复本次无迁移的生产切换。

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

旧镜像启动兼容已在隔离库验证，没有实际回退现网。上述是Web/API入口回退，不会撤回现已发布的Windows25文件。静态分发另保留在`client-public/releases/windows-0125-QG4pVi`中的发布前指针与配置；若需回退先按该run的脚本说明核对当前指针和保留的旧文件，不能删除已下载包或盲目重跑旧激活。Android清单未变化。不要将V33→V34旧演练再次跑在已V34生产上。
