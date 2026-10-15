# 0.1.9 导航体验发布

状态：云端、Windows 官网下载与可选更新源、登录后的 Android 更新源均已发布 0.1.9。Windows 正式覆盖升级与 Android 正式应用内升级、相关导航及会话/队列保留检查已完成。

Web、API、Windows、Android 版本为 0.1.9，Android 构建号 14。沿用原包名、签名、云端地址和数据，不新增数据库迁移。更新保持可选，自动下载默认关闭；官网 APK、二维码及公众注册继续关闭。

## 本次影响范围

修复新页面沿用上一页滚动位置的问题；进入不同页面或课程时回到顶部并聚焦内容区；历史返回等待异步列表后恢复位置，用户操作或再次导航会取消等待。同路径查询变化不强制滚动，官网锚点仍可跳转。

只验收导航、教学入口及升级保留。不重跑未变化的三模式业务写入、账号隔离、离线冲突或数据库恢复全套用例。历史完整测试结果保留在原记录，不作为本次实际执行结果。

## 已完成的验证

导航单元 11 项、桌面及手机网页导航 8 项通过；发布测试选择器的范围核验通过。Web/API、正式 Windows 及 Android 均已构建，Windows 原公钥签名和 Android 原证书、受限更新地址核验通过。

正式 Windows 已从 0.1.8 在原目录覆盖至 0.1.9，启动前 396 个原应用数据文件哈希不变。原账号、测试待办与 0/0 队列保留，正常关闭重开仍免登录。已实际操作长设置页进入教学、课程入口与历史返回；新页面从顶部展示，返回长页恢复到先前区域。正式版本页显示 0.1.9，云端地址正确，检查更新显示最新版本，自动下载未勾选。未编辑或同步真实业务。

生产部署目录为 `/home/oyts/plan-platform/releases/0.1.9-75f6e9405939`，升级前备份 `plan_platform-mysql-20261015-081346.sql.gz` 已通过 gzip 与 SHA256 校验。本次不重做未受影响的数据库恢复演练，旧镜像、数据库卷、旧服务与发布目录保留。

真实 HTTPS 桌面及手机网页导航 6 项、Windows 官网下载/签名/缓存/公开 APK 关闭检查 5 项、受邀 Android 清单与完整文件鉴权检查 4 项通过。手机由正式 0.1.8 检查到可选 0.1.9，手动下载后应用完成校验，再由维护者在系统安装器完成安装；不是使用 `adb install` 替代。升级前队列为 0 项操作、0 项冲突，不将本次检查表述为非空队列保留测试。

正式 Android 的长客户端页进入教学回顶、四节课程入口/返回顶部及导航后 0/0 队列比对已完成。最后的重开检查原先误以为仍停在同步页而停止；应用实际会恢复工作区，已修正测试等待条件，仅重跑该失败步骤，确认重开免登录、队列仍为 0/0。实际已安装 APK 的 SHA256 与下表一致。新增可见性守卫 4 项单元测试通过。真机范围限本次连接的 V2426A / Android 15，不推广为所有设备均已验收。

## 不可变安装包

候选目录：`production-artifacts/client-release/0.1.9/20261015T080518Z-a6b7a60d`。

| 文件 | SHA256 | 字节数 |
| --- | --- | --- |
| Plan Cabin_0.1.9_x64-setup.exe | `e63b5170fae9e022d48770b3db2fdd8df13b5dbd4ee5a700ef628e328da08c48` | 3468803 |
| PlanCabin-0.1.9-release.apk | `b071baef21171273f76b694678f1bdd0e219711ce6adfedccf14090095ebc5f8` | 3365802 |

Windows 沿用原 Minisign 更新公钥，无 Authenticode 发布者证书；Android 沿用 `cn.zijun701.plan.app` 和原签名证书。旧版本文件和标签不覆盖。

## 复现与回滚

小版本构建默认使用 `-TestScope affected`，根据当版 `scripts/release-test-scope.json` 选取相关用例；全面测试只在大版本显式选择 `-TestScope full`。本版导航检查可运行：

```powershell
powershell -File scripts/release-test-scope.test.ps1
npm --prefix web test -- src/router/scroll.test.ts
npm --prefix web run test:e2e -- e2e/navigation.spec.ts
node scripts/public-navigation-acceptance.mjs
```

使用项目既有 Node 22、Java 21、独立验收 API JAR 及 Playwright 浏览器目录。手机正式验收脚本为 `scripts/android-navigation-acceptance.py`，分为升级前记录和升级后检查；不输入登录密码，不向真实业务写入数据。

Windows 静态源回滚：

```sh
sh /home/oyts/plan-platform/client-public/releases/windows-019-DqM2hR/publish.sh rollback windows-019-DqM2hR
```

Android 受限源回滚：

```sh
sh /home/oyts/plan-platform/android-private/staging/android-14-b8a59ad5/rollback.sh
```

两项按实际问题分别回滚，不降级数据库；业务前一版本路径保留于当前发布的 `previous-release`。证据位于被 Git 忽略的 `acceptance-results/public-navigation/2026-10-15T08-14-43-932Z-741e2478/`、`windows-public-cloud/2026-10-15T08-21-03-813Z-6848b3d1/`、`invited-android/2026-10-15T08-15-44-729Z-bfbbe490/`，不上传私有账号或日志。

## 发布边界

Android 仍仅用于受邀测试及登录后的应用内更新，官网和 GitHub 不公开 APK。未迁移到受限更新通道的旧 Android 需要私下取得同包名同签名 APK，不能从已到期的匿名迁移窗口更新。Windows GitHub 附件只含 EXE、其签名与校验和。

本版不验证未变化的业务功能；过去完整回归的结果不能写成本版又做了一次。原生覆盖安装、应用内更新、浏览器视口和第三方厂商设备的验收范围分别记录，不相互替代。
