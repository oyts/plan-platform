# 部署清单

## 上线前

1. 复制 `.env.example` 为 `.env`，填写新的 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD` 与至少 32 位随机 `JWT_SECRET`；这些值绝不提交 Git。
2. 设置 `CORS_ALLOWED_ORIGINS=https://你的域名`、`APP_PUBLIC_URL=https://你的域名` 和 `REFRESH_COOKIE_SECURE=true`。
3. 默认保持 `REGISTRATION_ENABLED=false`。仅在明确邀请新用户时短暂开启，并在创建完成后关闭。
4. 若启用找回密码，同时配置 `PASSWORD_RESET_ENABLED=true`、`MAIL_FROM` 和 SMTP 参数。没有可用 SMTP 时保持该功能关闭。
5. 在反向代理或安全组层仅开放 80/443；数据库 3306 不对公网开放。

## 启动与验收

```bash
docker compose up -d --build
docker compose ps
curl -fsS http://127.0.0.1:43127/ >/dev/null
```

浏览器依次验证：登录、刷新页面后会话保持、创建计划、计时、导出、退出设备。修改密码后，确认所有设备都必须重新登录。

## 升级与回滚

1. 升级前备份 MySQL 卷或执行逻辑备份；Flyway 只允许向前执行迁移。
2. 拉取并校验 Git 提交，再执行 `docker compose up -d --build`。
3. 若应用异常，先保留容器日志与数据库快照；不要删除数据卷。回滚应用镜像前先核对新迁移是否与旧版本兼容。

## 运行期

- 每日备份数据库并定期恢复演练。
- 定期查看登录会话，及时撤销不认识的设备。
- 轮换 SMTP 授权码、数据库密码和 JWT 密钥时安排维护窗口，JWT 密钥轮换会使所有登录失效。
