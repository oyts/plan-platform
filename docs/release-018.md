# 0.1.8 教学系统发布与验收

状态：云端、Windows 官网下载、Windows 可选更新源及受登录保护的 Android 更新源已发布 0.1.8。Windows 正式覆盖安装、Android 正式应用内升级、升级后数据保留及正式 APK 的四节教学入口均已通过。

## 更新内容

新增周期计划、目标路线、今日待办、离线与同步四节互动教学。首次进入相关功能可开始教学或稍后再看；桌面主导航及手机“更多”的“使用教学”入口可随时回顾。案例运行于独立练习区，不向真实业务或同步队列写入示例数据。

教学进度按当前设备、服务器及账号保存，不跨设备同步。支持保存练习、继续、返回上一步和重新练习；持久化写入完成后才显示步骤、完成或跳过状态。完整功能说明见 [使用教学](learning-system.md)。

版本统一为 Web/API/Windows/Android 0.1.8，Android 构建号 13。沿用既有应用身份及签名。更新为可选，自动下载仍须本人主动选择；不开放公众注册、官网 APK 或二维码，不重新开放已到期的旧 Android 匿名迁移窗口。

## 已完成的验证

2026 年 10 月 15 日，后端 34 项、前端 156 项、签名与发布工具 34 项测试通过；完整本机浏览器回归 80 项通过、6 项按设计跳过。Android Java 13 项通过；Rust 9 项通过，1 项依赖本地 HTTPS 更新实验室的用例按原规则忽略。发布代理修复另有 2 项、升级版本/重入/可见性守卫 8 项、Android UI 输入与底部导航安全 11 项测试通过。

独立 Windows EXE 和 Android 真机各 13 项原生回归通过，含四节教学实际操作、保存重开、业务隔离、周期计划与休息日、路线阶段依赖、今日待办、离线队列、幂等重放及 409 冲突停靠。它们使用隔离应用身份、随机合成账号和内存数据库，不代替正式安装包的原账号保留或真实云端三端互通验收。

经维护者确认保存并关闭 PC 后，使用 `/UPDATE` 在原 Windows 目录从 0.1.7 覆盖至 0.1.8。启动前 386 个原应用数据文件 SHA256 不变，未卸载或清除凭据。正式客户端已核对云端连接、三种模式、原计划、0/0 同步队列、四节教学入口；关闭重开仍保持原账号登录。自动下载未勾选。

MySQL 升级前备份 `plan_platform-mysql-20261015-041049.sql.gz` 的 gzip 与 SHA256 校验通过；恢复到独立库 `plan_restore_20261015_041050` 后，全部表校验和与原库一致。原库和演练库都保留。

生产部署目录为 `/home/oyts/plan-platform/releases/0.1.8-efb9dbe708dd`，部署前另有已校验备份 `plan_platform-mysql-20261015-041240.sql.gz`。公网健康返回 `plan-platform-api` / `0.1.8`，公开注册关闭、邀请注册开启，旧服务、数据库卷和旧安装文件保留。生产 HTTPS 桌面及手机网页的四节教学、保存刷新、导航回顾与业务不变检查共 6 项通过。

Windows 公网 EXE 字节与下表一致，更新清单不缓存，官网两种视口均可下载；APK、旧匿名清单及官网二维码仍关闭。Android 受限清单和 APK 的匿名/无效凭据拒绝、两个受邀普通账号绑定与完整文件哈希共 4 项通过，Windows 静态源切换后再次核对通过。

正式网站桌面及手机网页业务回归 27 项通过。真实 HTTPS 云端、Rust Windows、Java Android 和浏览器的 10 项联测通过，覆盖四节教学、三模式交叉修改、休息日、路线前置依赖、会话重开、409 拒绝覆盖及账号隔离；Android 业务没有通过 USB 转发到本机 API。

Android 正式 0.1.7 / 12 → 0.1.8 / 13 的应用内更新已通过：原账号登录后取得受限清单、手动下载并完成完整性/签名核验、请求系统安装，由维护者亲自确认。已安装 `base.apk` 的 SHA256 与下表完全一致，未通过 `adb install` 替代该升级流程。

升级前使用一条独立合成待办验证真实断网编辑、关闭重开与 1 项本地操作、0 项冲突，Wi-Fi 与移动数据均恢复原状态。升级后及再次重开仍免登录、保留离线标题和 1/0 队列；三模式可加载。通过队列自身的请求详情证明唯一操作是本次合成待办后同步，结果为 0 项操作、0 项冲突。没有提交未知队列或编辑其他真实记录。正式 PC 刷新后已显示同步后的合成标题。

正式 APK 的“更多 → 使用教学”入口、四个课程首步和独立练习区提示均已通过只读自动检查，回到同步中心仍为 0/0。课程完整交互与保存重开由前述独立原生及真实云端联测覆盖；未自动重置维护者的教学进度。

正式 UI 驱动曾因同名“计划”表单控件与底部链接、长页面下不可见的请求详情而安全停止，没有误点或提交；已增加明确的底部导航定位、模式重入及可见性守卫，并完整重跑通过。

## 发布代理修复

受限 Android 发布会单独重建后端容器，其 Docker IP 变化时，未重载的 Nginx 仍使用旧地址并短暂返回 502。此次已通过容器内健康检查定位，执行 `nginx -t` 和 `nginx -s reload` 恢复公网，再完成鉴权与下载验收。发布脚本已补充激活、失败回滚和保留回滚脚本中的代理校验与重载。

本次服务器上原先生成的 Android 回滚脚本保留不变。如需回滚受限更新配置，执行后还需运行：

```sh
docker exec plan-platform-web-1 nginx -t && docker exec plan-platform-web-1 nginx -s reload
```

Windows 静态下载和受限 Android 清单均有独立回滚，不会降级客户端或删除数据库。服务器上对应命令为：

```sh
sh /home/oyts/plan-platform/client-public/releases/windows-018-fT5jwA/publish.sh rollback windows-018-fT5jwA
sh /home/oyts/plan-platform/android-private/staging/android-13-cf83e8cb/rollback.sh
docker exec plan-platform-web-1 nginx -t && docker exec plan-platform-web-1 nginx -s reload
```

两项配置按实际故障分别回滚，无需为单个下载问题切换整套业务。业务镜像的上一发布目录保留在当前发布的 `previous-release` 中；回滚业务前先核对现有数据和该次部署记录，不把数据库备份覆盖回已产生新数据的正式库。

## 不可变候选文件

候选目录：`production-artifacts/client-release/0.1.8/20261015T040423Z-c89dfa08`。不覆盖原文件，不替换旧版本安装包或标签。

| 安装包 | SHA256 | 字节数 |
| --- | --- | --- |
| Plan Cabin_0.1.8_x64-setup.exe | `679810f4291361755587a3b785b7baec27d4e7cbdf4ea02ea295c3cf96932c1b` | 3468150 |
| PlanCabin-0.1.8-release.apk | `4071d7489857fc42056fde6e87aac4619194ad27388efe1747503259557dab6b` | 3365386 |

Windows Minisign 签名已按原更新公钥验证；未配置 Authenticode 发布者证书。APK 包名为 `cn.zijun701.plan.app`，构建号 13，原证书 SHA256 为 `4528eeee02c43df6a4429481c99f6c39d459c82d9ca1c538f316e2ff37c68e9d`，无调试及明文 HTTP，实际字节码绑定登录后的受限更新接口。

## 发布边界与后续优化

Android 官网下载、二维码和公开注册仍关闭，APK 只提供受邀测试及登录后的更新。当前真机证据限 V2426A / Android 15，不推广为所有厂商均已验收。GitHub 公开附件只允许 Windows EXE、签名和校验和，旧标签与文件不覆盖。

正式 Android 导航中观察到部分路由会沿用前一页滚动位置，课程导航可能需要向上滚动才能看到。自动验收会先滚动到可见控件，不盲点被底栏遮挡的位置。此项作为下一版的导航体验优化保留，不在发布后悄悄替换已签名 0.1.8 文件。

## 验收复现

在项目根目录运行；云端用例只使用事先准备的两个专用受邀普通账号，凭据留在被 Git 忽略的本机受限目录。Android 需要已授权的 USB 调试连接，独立测试包的安装提示由本人确认。

```powershell
powershell -File scripts/native-regression.ps1 -Action build
powershell -File scripts/native-regression.ps1 -Action run -Platform android -Serial <设备序列号>
powershell -File scripts/cloud-native-acceptance.ps1
powershell -File scripts/public-web-acceptance.ps1
node scripts/public-learning-acceptance.mjs
```

正式 APK 升级保留验收使用 `android-offline-upgrade-baseline.py` 与 `android-post-upgrade-acceptance.py`。后者必须显式传入 `--expected-version 0.1.8 --expected-code 13`，只允许 `nativeupgrade-` 合成待办；存在其他队列项时拒绝自动同步。脚本明确定位底部导航，拒绝同名表单控件、未登录页面、锁屏及其他应用。

本机证据目录保留于 `acceptance-results/`，不公开真实会话、账号或日志：

- Windows 原生：`native/2026-10-15T04-10-17-636Z-5fe54a50/`
- Android 原生：`native/2026-10-15T04-23-37-006Z-06675116/`
- 真实云端三端：`cloud-native/2026-10-15T04-27-37-579Z-03e2dc3a/`
- 正式网页业务：`public-web/20261015-042635-67a90e/`
- 正式网页教学：`public-learning/2026-10-15T04-13-54-646Z-5202dff2/`
- Windows 官网下载：`windows-public-cloud/2026-10-15T04-26-19-989Z-be5bcf05/`
- Android 受限源：`invited-android/2026-10-15T04-28-10-829Z-2cd93835/`
