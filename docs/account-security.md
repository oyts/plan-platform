# 账号与会话安全

## 当前实现

- Access Token 使用 HS256 JWT，默认有效期 15 分钟，只保存在 Web 客户端内存中。
- Refresh Token 使用高熵随机值，数据库只保存 SHA-256 摘要；浏览器通过 `HttpOnly`、`SameSite=Strict` Cookie 携带。
- 每次刷新都会撤销旧 Refresh Token 并签发新令牌，旧 Cookie 不能重放。
- 单账号最多保留 5 个活跃刷新会话，可查看、单独撤销或全部撤销。
- 登录失败同时按“邮箱+来源地址”和“来源地址总量”限流。当前为单实例内存实现，横向扩容前迁移到 Redis。
- 公开注册默认关闭，通过 `REGISTRATION_ENABLED=true` 显式开启。
- 注册密码为 10—72 位，至少包含一个英文字母和一个数字；密码使用 BCrypt cost 12 保存。
- 用户可修改显示名称；邮箱继续作为不可直接变更的登录标识，邮箱变更需要后续的双邮箱验证流程。
- 修改密码时必须验证当前密码，并撤销该用户全部刷新会话。
- 找回密码默认关闭。启用后由 SMTP 向已注册邮箱发送一次性、高熵的重置链接；数据库仅保存令牌 SHA-256 摘要。请求接口始终返回相同结果，避免枚举邮箱。

## 生产环境变量

```text
JWT_SECRET=<至少 32 字符的高熵随机值>
JWT_ACCESS_TOKEN_MINUTES=15
JWT_REFRESH_TOKEN_DAYS=30
REGISTRATION_ENABLED=false
REFRESH_COOKIE_SECURE=true
MAX_LOGIN_ATTEMPTS=5
LOGIN_WINDOW_SECONDS=900
MAX_SESSIONS=5
PASSWORD_RESET_ENABLED=false
PASSWORD_RESET_MINUTES=60
APP_PUBLIC_URL=https://plan.example.com
MAIL_FROM=no-reply@example.com
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=<SMTP 用户名>
SPRING_MAIL_PASSWORD=<SMTP 授权码>
```

生产环境必须使用 HTTPS，并保持 Web 与 API 同站部署。`REFRESH_COOKIE_SECURE=true` 时，浏览器不会在 HTTP 连接中发送刷新 Cookie。

## 已知边界

“退出全部设备”会立即撤销所有刷新会话；已经签发的 Access Token 最长还能存活 15 分钟。若未来业务要求立即封禁，应增加用户令牌版本或集中式 JWT 黑名单。当前版本尚未实现邮箱验证、邮箱变更和管理员后台。这些能力应在面向陌生用户开放公共注册前完成；目前的密码找回仅适合已配置 SMTP 的受控小范围使用场景。
