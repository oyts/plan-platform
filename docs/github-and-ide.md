# GitHub 与 IDE 使用

远程仓库为 `https://github.com/oyts/plan-platform.git`。Git 配置保存在项目根目录的 `.git` 中，IDEA 和 VS Code 使用同一目录时会自动共享提交、分支和远程地址。

## 在当前电脑打开

1. IDEA 选择“打开”，选择 `E:\Ai\假期计划\plan-platform`，将 `backend/pom.xml` 作为 Maven 工程导入，项目 SDK 选择 Java 21。
2. VS Code 选择“打开文件夹”，选择同一个 `plan-platform`；前端源码在 `web`，可以在根目录终端执行 `cd web`。
3. 在任意一个 IDE 的终端执行 `git remote -v`，fetch 和 push 应均指向 `oyts/plan-platform.git`。
4. 修改前先 `git pull --ff-only`；修改后检查差异、运行对应测试、提交，再推送。不要在两个 IDE 同时执行提交、切分支或变基。

不需要“重新初始化 Git”，也不要把 `backend` 和 `web` 分别建立成嵌套仓库。在新电脑上先 `git clone`，再打开克隆后的目录即可。GitHub 账号认证使用 Git Credential Manager 或 SSH；不要把密码或令牌写进远程 URL。

## 日常命令

```bash
git status
git remote -v
git pull --ff-only
git switch -c feature/your-feature
git diff
# 运行测试后，按实际文件选择 git add，再提交
git commit -m "feat: describe the change"
git push -u origin feature/your-feature
```

不要直接执行 `git add .` 上传未知文件。`.env`、数据库、测试结果、安装包、工具目录与签名私钥应保持在忽略规则中；公开历史也要检查，不只是当前文件。忽略规则不会删除已经进入 Git 历史的秘密，误上传后必须立即撤销凭据。

## 用于求职介绍

可以如实描述：基于 Java 21、Spring Boot、Vue 3 的多用户任务管理项目，实现 Spring Security 认证、用户级数据隔离、Flyway 迁移、乐观锁冲突检测、幂等写入、IndexedDB 离线队列，以及 Tauri Windows / Capacitor Android 复用前端。用自动测试和真实验收记录展示这些实现。

已完成新版 HTTPS 部署、MySQL 备份恢复演练和正式签名候选包验收；Windows 官网下载与更新源已启用，Android 仍为受邀私测。不要写成高并发商业运营、自动无冲突合并、Android 公开上架或微信小程序上线。面试时应能解释令牌轮换、409 冲突、同一幂等键的重复响应、离线缓存隔离，以及 H2 和 MySQL 的验证差异。每个版本的实际验收与待办见发布记录，不能以构建成功代替真机结果。
