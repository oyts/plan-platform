# 六阶段候选部署与验收交接

六阶段新增源码统一为 **0.1.15，Android 构建 20**。目前仅为本地签名候选，正式网站、Windows 官网及更新源、Android 登录受限更新源仍是 0.1.14／19。本轮没有生产切换、正式覆盖安装、公开注册或 APK 发布，也没有宣布 1.0。

## 已准备的产物

| 产物 | 本机相对仓库路径 | 校验 |
| --- | --- | --- |
| API 与 Web 部署归档 | `production-artifacts/plan-platform-0.1.15.tar.gz` | SHA-256 `792a5d8a412d6e650833592043d87de9f31aa9a9e146e7b390bc374fa310b1b6` |
| 最终 API | `backend/target-acceptance/plan-platform-api.jar` | SHA-256 `6a68b763d7b05c48aaaae379eb7ba9eb313840ab28e79b4b3333039823690013` |
| Windows 签名候选 | `production-artifacts/client-release/0.1.15/20261018T075011Z-49f03c07/windows/Plan Cabin_0.1.15_x64-setup.exe` | SHA-256 `52067147ea37c147bb6362b3e4aa26c6223b39fdae95326b9e1bbc4a15509476` |
| Android 签名候选 | 同一目录的 `android/PlanCabin-0.1.15-release.apk` | SHA-256 `19fd4815f6477716b4eea170e30b6f381b82589449a2d04d9f0b45e6a448e9ee` |

Windows 更新签名已用原公钥验证，不等于已提供 Authenticode 发布者证书。Android 原包名 `cn.zijun701.plan.app`、原证书、非 debug、禁止明文网络，内嵌登录受限更新源；没有换签名、覆盖密钥或安装到设备。以上候选不能在完成安装验收前推送给朋友或更新通道。

独立测试 EXE/APK 位于 `.desktop-tools/native-regression/`，身份与正式应用不同。清单源码指纹 `777cbeaf8c304e9ea61c7967fea879f94ee18162ea5a3d203a6a14ca74961225`、API hash 已与最终构建核对；这不是正式升级数据保留证据。

## 可复现构建顺序

1. 读取 `AGENTS.md`、`roadmap-progress.md` 和本版测试范围，保留用户改动。先完成对应阶段的新增及直接受影响测试，不重跑无关业务。
2. 使用 JDK21、Maven3.9、Node22 构建 API/Web，运行 `node scripts/verify-release.mjs`。Android20 必须高于正式19，三端业务版本统一0.1.15。
3. `scripts/prepare-cloud-release.ps1 -SkipTests` 只用于本轮已有定向证据后的构建归档；它的含义是跳过测试，不得将输出写成又通过了一轮测试。最后 JAR 再执行隔离 MySQL 读取／校验，本轮结果见性能基线。
4. `scripts/native-regression.ps1 -Action build` 只构建独立客户端，不探测或安装手机；清单必须绑定最后的 API 与共享源码。
5. `scripts/prepare-formal-clients.ps1 -TestScope affected` 使用现有受限签名配置，输出到全新不可覆盖候选目录。签名、公钥、包身份、哈希和更新源由工具验证，不能以 debug 或实验密钥替代。

归档使用显式文件清单，没有 `.env`、数据库、签名私钥或本机测试资料。本轮补入运维采集器、治理回退守卫与默认不启用的运维模板；模板不是生产开关，也没有自动安排采集、OSS 下载或邮件。

## 下一次生产升级之前

- 用户先确认发布与窗口；检查当时正式版本、容器、队列和运行中的 AI 任务。不要凭本次早先状态直接切换。
- 对当时真实 MySQL 重新备份并核对独立恢复路径；本轮合成 V1—V23 与旧镜像演练不替代真实升级前备份。保留旧 Node、原生包、旧镜像、卷、发布和签名资料。
- 新版保持原域名、HTTPS、Secure Cookie、CORS、受限 AI／邮件挂载和已有下载叠加配置。治理和运维默认关闭；实际停用策略、元数据权限与采集周期需确认后才启用。
- 如果需要运维，先验证宿主机目录与只读挂载，再从模板准备受限 `production-operations.yml`。`production-compose.sh` 仅保留已经批准存在的叠加文件，不替用户创建或启用它。
- 核对 `/api/health`、`/api/auth/config`、登录及本次新增业务，失败保留新数据并恢复已验证的入口／镜像，不降级数据库。
- 回退到不识别会话代次的旧服务前，必须通过 `check-governance-rollback.py --target-supports-generation no`。发生过成员治理时拒绝该回退；不得清零代次来绕过。

## 仍需用户参与的门槛

Windows 独立 0.1.15 已完成登录后的九组定向界面验收：反馈草稿重开与同意提交、原生文件选择器预检／恢复防重、周期结构复制、路线分页／暂停恢复、待办历史、周复盘离线草稿重开、真实409三方人工选择与后续队列、七天预加载离线重开、运维与治理只读页面。用户自行登录合成账号，使用真实 Rust 连接桥和隔离 API。详细范围、两段夹具恢复及未覆盖分支见 `roadmap-progress.md` 的“Windows 六阶段定向验收”。

正式 PC 覆盖仍需用户保存并关闭后明确确认；非空合成队列升级保留与空队列保留应分开记录。独立 PC 的管理员回复、未来批量409分支和导出保存目标没有补测，不把 Android／共享源码结果算作 Windows 操作；成员安全设置不通过 Windows 自动化修改。

Android 已在用户连接、解锁并授权后完成独立 0.1.15 真机新增业务验收，11个业务场景通过，详见 `roadmap-progress.md` 的“Android 六阶段定向验收”。使用真实 Java 桥和隔离 API，不是生产联测；文件恢复使用合成 File 注入，不含系统文件选择器／分享目标。正式手机仍为0.1.14／19，同签名正式升级和数据／队列保留还未执行，需要用户保存并确认覆盖及相应服务条件。系统安装确认由用户操作，不跑无关旧逻辑全量回归。

安装与真实云端新增业务门槛通过后，才可发布 Windows 下载／可选更新和登录受限 Android 源。官网 APK／二维码、公开注册、必更与备案继续单独确认；GitHub 源码推送不等于已经上线。
