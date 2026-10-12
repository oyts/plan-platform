# 正式客户端签名与 APP 备案准备

0.1.4 的 Windows 与 Android 正式签名候选包已在本机构建并核验，但尚未公开发布。Android 构建号为 8，使用新包名 `cn.zijun701.plan.app`；原来调试签名的 `cn.zijun701.plan.mobile` 保留，不能使用新签名直接覆盖。正式新包首次安装需重新登录相同云端账号，只能读取已经同步到云端的数据，旧包的本地离线队列不会自动迁移。

## 本轮结果与发布条件

2026 年 10 月 12 日前端 129 项、Android Java 11 项、签名与备份 6 项、更新场景 7 项测试通过。Windows 安装包的 Minisign 载荷签名和可信注释签名通过真实密码学验证；Android APK 通过 apksigner 验证，包名、版本和签名证书与长期身份一致，关闭调试与明文 HTTP。这些结果不替代正式安装包的实机登录、重开和三模式操作验收。

本轮候选包位于 `production-artifacts/client-release/0.1.4/20261012/`，其中 `metadata/candidate.json` 保存大小、SHA256 和签名核验结果。`readyForPublicDownload` 保持 `false`；`downloads-manifest.json` 中两个平台均为 `null`。`downloads-manifest.pending.json` 只是将来发布的草案，不能直接上线。

随后成员页手机适配修复生成了新候选目录 `production-artifacts/client-release/0.1.4/20261012T104035Z-73ec9759/`，两端签名与 APK 身份重新核验通过，旧候选文件没有覆盖。两个目录都是未公开发布的候选构建，不是同一版本公开发布后替换文件；正式版本一旦发布，升级必须递增版本与 Android 构建号。

经用户允许，正式 Android 新包已在 iQOO V2426A 上安装并启动，版本为 0.1.4 / 构建 8；原 `cn.zijun701.plan.mobile` 仍为 0.1.3 / 构建 7。安装成功不等于云端业务与重开验收通过，这两项仍由用户确认。

成员页修复候选包随后经用户允许覆盖新正式应用，未卸载或清数据；设备上 APK 的 SHA256 与新候选文件相同。用户已确认成员页布局正常，正式管理员会话保持仍待单独确认。独立原生客户端的真实云端自动化重跑 9 项通过，不能将独立回归包的结果替代这项正式管理员安装验收。

Windows 已从 0.1.3 原目录覆盖为 0.1.4，使用 NSIS 的 `/UPDATE` 路径，不执行卸载。旧程序与本地应用数据先备份到受限目录，首次启动前 350 个原应用数据文件哈希与安装前一致；未导出或清理 Windows 登录凭据。实际 EXE 版本与长期更新公钥已核验，用户已切换至正式 HTTPS 地址，配置读取也确认保存正确。安装记录为 `.desktop-tools/formal-windows/private/20261012T111447Z-cc228d67/installation.json`。

本轮真实云端 Windows、Android 与网页联测再次 9 项通过，包括原生登录、周期计划与休息日、目标路线依赖、今日待办、重开会话、409 冲突保护和账号隔离。结果位于 `acceptance-results/cloud-native/latest.html`，只使用受邀请普通验收账号及独立回归包，不修改管理员数据。

正式 Windows 安装包的界面验收随后完成。用户自行登录云端账号后，实际原目录应用正常打开周期计划、目标路线及今日待办，同步中心显示 0 项操作、0 项冲突；原生“测试连接”明确识别 Spring Boot 多用户服务，安装版本为 0.1.4，自动下载保持关闭。通过窗口关闭按钮正常退出，再启动同一 EXE 后保持登录并恢复目标路线页面，没有临时开放调试接口。该账号当前业务列表为空，本轮没有新增、编辑、删除或同步真实记录；既有数据交叉读写及隔离仍由上述独立回归验证。

正式双端更新清单尚未发布，两个 `client-updates/.../latest.json` 地址只读检查均为 HTTP 404。正式 PC 因此显示更新清单获取失败，不代表业务 API 连接失败，也不算正式应用内升级通过；没有关闭 TLS 或绕过签名校验。

加密签名备份与真实密钥隔离恢复演练已完成：恢复的 Android 私钥能生成证书请求，证书与原身份相同；恢复的 Windows 私钥签名经原公钥验证。原六个签名文件哈希未改变，用户已在本机确认口令独立保管；受限记录 `backup-check.json` 中恢复证明为通过，独立副本仍为未确认。本机文件不替代离线盘或私有网盘副本。

公开发布前仍需完成长期密钥的独立备份副本核对、正式 Android 新包会话与三模式验收及维护者确认。Android 公众下载另按用户决定等待 APP ICP 备案；公开注册继续关闭。没有自动购买服务、提交备案或上传安装包。

## 长期签名身份

长期文件放在 Git 忽略的 `.release-signing/`，目录仅允许 Windows OYTS 用户与 SYSTEM 访问。Windows 使用新 Tauri 更新签名私钥；Android 使用 RSA 3072 位、约 30 年有效的 PKCS12 密钥，别名为 `plan-cabin-release`。密钥密码随机生成，以 Windows 当前用户 DPAPI 加密保存，没有写入源码或聊天。

DPAPI 依赖 Windows 用户与机器。只复制 `credentials.dpapi` 到另一台电脑不能恢复密码；完整可迁移备份必须同时包含密钥和解密后的密码，并用独立备份口令重新加密。

Windows 更新签名不是 Authenticode 发布者证书。本阶段未配置受信任的 Windows 发布者代码签名，系统可能显示未知发布者或 SmartScreen 提示。不要将这些提示描述为更新签名失败，也不要让用户关闭系统安全防护。

## 维护者检查与离线备份

以下命令在本机 Windows PowerShell 执行，不是在 ECS 的 SSH 终端执行：

```powershell
cd E:\Ai\假期计划\plan-platform
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 -Action check
```

用户选择先在本机生成加密备份，再自行上传到自己的私有百度网盘。双击 `prepare-signing-backup.bat` 使用手工口令；也可让脚本安全随机生成口令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-signing-backup.ps1 -AutoPassphrase
```

脚本在 `.desktop-tools/signing-backups/private/` 新建受限目录。自动方式使用密码学随机的 32 字节生成 43 字符独立口令，先以当前用户 DPAPI 保护，再完成备份与恢复证明；口令只在本机对话框显示，由本人复制到独立密码管理器并确认保存，不输出到终端或聊天，也不放入命令参数。取消对话框不会删除备份，但不会记录口令保管确认。临时剪贴板仍为该口令时，对话框关闭会清除它。

不加 `-AutoPassphrase` 时，需隐藏输入至少 16 字符的独立备份口令两次，再为恢复演练输入一次同一口令。原签名目录始终保留。备份完成并验证之前不要关闭窗口；口令不要与备份同处保存，不要发到聊天。`passphrase.dpapi` 依赖本机用户，不能当作跨电脑恢复口令，也不要上传到网盘。

若直接保存到自己的离线存储盘，将下方 `X:` 改为实际盘符：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 `
  -Action backup -BackupPath 'X:\PlanCabin-20261012.plan-signing-backup'
```

备份使用 scrypt 派生密钥与 AES 256 GCM 认证加密，并在写入前解密核对内容；拒绝覆盖已有文件。只放在本机硬盘的另一份文件不算独立备份。只上传加密的 `.plan-signing-backup` 文件到自己的私有云盘，不上传 `.release-signing`、恢复演练目录或明文口令；私有云盘副本须由本人核对，脚本不自动上传，也不会因本地备份成功而开放公众下载。

单独检查备份或做隔离恢复演练：

将下方“完整备份文件路径”替换为备份窗口输出的实际 `.plan-signing-backup` 文件路径，不要原样复制占位文字。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 `
  -Action verify-backup -BackupPath '完整备份文件路径'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 `
  -Action restore -Rehearsal -BackupPath '完整备份文件路径'
```

恢复演练只写入 `.desktop-tools/signing-restore/private/` 下的新目录，以当前 Windows 用户重新保护密码。它实际打开恢复的 Android keystore 并比对原证书，再用恢复的 Windows 私钥签名一份无业务内容的文件，以原公钥验证。错误口令、缺失文件、路径越界条目及哈希不匹配会停止。只有这项实际演练成功才生成 `backup-check.json`；加解密单元测试不代替真实密钥恢复证明。

新维护电脑需要恢复正式身份时，先安装构建工具，在没有现存签名文件的环境使用 `-Action restore -BackupPath ...`，不加 `-Rehearsal`。脚本拒绝覆盖现有身份，不应删除当前环境来演练。备份包含私钥及密码，即使加密也不要加入 Git、公开 OSS 或官网下载目录。

## 可复现构建与核验

签名身份仅初始化一次，已有身份会检查而不会重新生成；部分密钥目录会令初始化停止。不要为了解决构建错误删除或换掉密钥。

```powershell
# 仅在尚未创建长期身份的维护环境执行一次
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/release-signing.ps1 -Action init
# 每次创建独立候选目录，构建与核验，不安装或上传
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/prepare-formal-clients.ps1
```

可双击 `prepare-formal-clients.bat`。如果官方 Maven Central 经已有代理返回 403、但直连正常，可增加 `-DirectMavenCentral`。它只让当前构建 JVM 直连 `repo.maven.apache.org`，其他依赖继续使用已有代理，不改系统网络设置、不关闭 TLS、不使用未知镜像。

Windows PowerShell 5 的 JSON 读取显式指定 UTF8，避免中文窗口标题造成解析失败。正式构建拒绝回归包、测试 TLS 和测试版本覆盖。更新清单生成器默认校验长期证书与真实 Windows 签名；只有显式 `-ForTesting` 才使用旧测试身份，测试产物不可公开发布。

## APP 备案技术材料

`metadata/filing/android-app-features.json` 导出以下公开信息：

- APP 名称：计划舱。
- Android 包名：`cn.zijun701.plan.app`。
- 版本：0.1.4，构建号 8。
- 后端域名：`plan.zijun701.cn`。
- `publicKeyModulusHex`：RSA 公钥模数的十六进制值。
- `certificateMD5`：签名证书 MD5 的十六进制值。
- `certificateSHA256`：内部核验指纹，不要误填到要求 MD5 的备案字段。

同时导出公开公钥 PEM 与证书 DER，不包含 keystore、私钥或密码。阿里云 [APP 特征信息说明](https://help.aliyun.com/zh/icp-filing/basic-icp-service/user-guide/fill-in-app-feature-information) 要求安卓包名、公钥模数和证书 MD5，也支持上传 APK 智能提取。维护者可用这份真实 APK 与材料核对填写；个人身份资料仍由本人提供，APP 备案号未取得前不生成虚假号码。

## 正式安装验收

Android 新包与旧包并存安装，先确认旧包待同步队列已妥善保留，再在新包登录同一云端账号，检查周期计划、目标路线、今日待办及关闭重开。不要用卸载旧包来绕过签名不一致。

Windows 候选包使用新的更新公钥，旧测试公钥不会自动信任它。首次改用长期身份应由用户保存编辑并关闭客户端后手动安装，再验收原数据与配置保留；不通过旧测试源推送新身份，也不强杀正在编辑的客户端。本轮未证明新正式身份的跨版本应用内升级，需有更高版本同签名包再单独验收。

原目录覆盖由 `scripts/install-formal-windows.ps1` 执行，需显式 `-ConfirmedClosed` 且不能存在正式客户端进程。它核验候选安装包哈希和真实长期签名，先保存旧程序与应用数据，再核对安装后、启动前所有原数据文件未改变。备份目录受当前 Windows 用户与 SYSTEM 权限保护，但可能包含离线缓存及账号信息，不要将整个目录作为公开验收材料或未加密上传网盘。Windows 凭据管理器未导出，文件备份也不能替代凭据恢复。

本阶段签名、备份格式与更新场景 15 项测试通过，四个 PowerShell 脚本语法检查通过；另实测拒绝覆盖现有签名目录。真实密钥备份、隔离恢复和口令独立保管确认均已完成，网盘或离线盘副本仍待本人保存并核对；单元测试不能代替上述恢复证明。
