# 正式网站 Python 自动验收

本工具通过 Python 与 Playwright 在真实 HTTPS 网站 `https://plan.zijun701.cn` 操作周期计划、目标路线和今日待办。浏览器使用独立临时环境，不读取现有浏览器资料，也不使用管理员密码。桌面宽度 1280 与手机网页宽度 390 分别执行相同业务检查；手机网页视口不代表 Android APK 真机通过。

2026 年 10 月 12 日，正式 HTTPS 环境的完整运行结果为 **27 项通过、0 失败**。包括两种视口的三模式业务、缓存离线编辑、队列重放与真实 HTTP 409 冲突拒绝。此前日程下拉框名称和异步加载导致的脚本定位失败已修正；未据此修改线上业务服务。测试使用独立账号，不影响管理员计划。

## 数据与权限边界

业务验收必须使用两个受邀请的普通 USER 账号。当前本站专用邮箱为 `web-acceptance-a-20261012@example.test` 与 `web-acceptance-b-20261012@example.test`。脚本只创建和修改带“自动验收”标记的自有计划、任务、路线与待办；跨用户验证只尝试访问这些合成记录，不读取管理员数据。重复运行会生成新的合成记录，不自动删除账号或清理业务数据。

管理员在正式网站的成员邀请页面为这两个邮箱分别创建邀请，将完整注册链接一行一条保存到 `.desktop-tools/public-web/private/invitations.txt`。不要把链接发到聊天或提交到 Git。脚本会自动注册，随机强密码保存在同目录的 `accounts.json`；之后复跑使用原测试账号，不再消耗邀请。该目录应只允许本人、SYSTEM 与受信任的测试进程访问；不要向 Everyone 开放权限。

`.desktop-tools/`、`acceptance-results/` 和 Python 缓存均被 Git 忽略。邀请、密码、Cookie 和访问令牌不进入验收报告；不生成含敏感会话的 HAR 或浏览器 trace。验收结束只撤销此次新建的测试登录会话，不撤销原管理员会话。

## 运行

本机虚拟环境和 Chromium 已准备好，可以双击项目根目录的 `verify-public-web.bat`。也可以从项目根目录运行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/public-web-acceptance.ps1
```

仅检查官网、HTTPS、登录入口和下载禁用状态，不注册账号或写业务数据：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/public-web-acceptance.ps1 -PublicOnly
```

只复跑手机网页视口可增加 `-Profile mobile-web`，桌面则用 `-Profile desktop`。默认两者全部运行。脚本固定本站域名和专用账号，不能直接对另一个部署者的生产环境使用。

新电脑先安装 Python 3.11 或更新的兼容版本，再在项目根目录准备隔离依赖：

```powershell
python -m venv .desktop-tools/public-web/venv
& .desktop-tools/public-web/venv/Scripts/python.exe -m pip install -r scripts/requirements-web-acceptance.txt
& .desktop-tools/public-web/venv/Scripts/python.exe -m playwright install chromium
```

默认使用 Playwright 安装的 Chromium；本机已有验收浏览器时优先复用。也可以通过 `-ChromiumPath` 指定完整可执行文件路径。HTTPS 与证书检查保持启用，不允许忽略 TLS 错误。

## 检查范围与报告

检查包含官网三模式预览、ICP备案链接、未发布安装包的禁用状态、Secure HttpOnly Cookie、刷新登录状态和普通用户权限；周期计划休息日选择及二次编辑、任务编辑面板、分页、软删除恢复；目标路线阶段、指定前置解锁、搜索与分页；今日待办新增编辑、完成筛选、删除恢复；三模式位置记忆、跨用户读取与写入隔离。

离线检查中断真实 API 请求，但保持网页资源可加载，并禁用 Service Worker，以单独验证账号快照与 IndexedDB 队列。包含待办离线新增、缓存任务与路线内容编辑、刷新保留、恢复连接后顺序同步和重复记录检查。它不是浏览器彻底断网后的 PWA 冷启动验收。多设备冲突通过同一普通账号的合法 API 更新制造旧版本写入，要求返回真实 HTTP 409、撤回勾选且保留另一设备修改。

每次运行生成 `acceptance-results/public-web/<运行标记>/report.json`、`report.html` 与业务截图，最新摘要是 `acceptance-results/public-web/latest.html`。遇到失败会停止，不跳过失败继续报告成功。复跑会保留前次报告与合成数据；只有最终完整运行通过才算该范围验收完成。
