# 账号与会话安全

本页核对基线为已发布0.1.19／Android24。以下“默认关闭”指新部署的安全默认值，不表示现有生产仍未启用。生产已开启SMTP找回、邮箱验证、邀请及经确认的普通成员治理；邀请、验证、找回邮件均不向管理员抄送，投递状态不含完整敏感链接。见[0.1.16记录](release-0116.md)与[当前交付](stable-handoff.md)。

## 当前实现

- Access Token 使用 HS256 JWT，默认有效期 15 分钟，只保存在 Web 客户端内存中。
- Refresh Token 使用高熵随机值，数据库只保存 SHA-256 摘要；浏览器通过 `HttpOnly`、`SameSite=Strict` Cookie 携带。
- 每次刷新都会撤销旧 Refresh Token 并签发新令牌，旧 Cookie 不能重放。
- 单账号最多保留 5 个活跃刷新会话，可查看、单独撤销或全部撤销。
- 登录失败同时按“邮箱+来源地址”和“来源地址总量”限流。当前为单实例内存实现，横向扩容前迁移到 Redis。
- 公开注册默认关闭，通过 `REGISTRATION_ENABLED=true` 显式开启。
- 注册密码为 10—72 位，至少包含一个英文字母和一个数字；密码使用 BCrypt cost 12 保存。
- 0.1.5 注册页要求再次输入密码；不一致时不发送注册请求。确认密码不提交到后端，也不写入缓存或日志。
- 用户可修改显示名称；邮箱继续作为不可直接变更的登录标识，邮箱变更需要后续的双邮箱验证流程。
- 修改密码时必须验证当前密码，并撤销该用户全部刷新会话。
- 找回密码默认关闭。启用后由 SMTP 向已注册邮箱发送一次性、高熵的重置链接；数据库仅保存令牌 SHA-256 摘要。请求接口始终返回相同结果，避免枚举邮箱。
- 公开注册关闭时，只有管理员创建的一次性邀请链接可以注册；邀请令牌仅保存 SHA-256 摘要。管理员只能管理邀请，不能查看用户的计划数据。
- 邮箱验证默认关闭。开启后，注册用户会收到一次性验证链接；是否强制把“已验证”作为访问前提将留待后续风控阶段按业务决定。

## 新部署环境变量示例（不是当前生产配置）

```text
JWT_SECRET=<至少 32 字符的高熵随机值>
JWT_ACCESS_TOKEN_MINUTES=15
JWT_REFRESH_TOKEN_DAYS=30
REGISTRATION_ENABLED=false
REFRESH_COOKIE_SECURE=true
MAX_LOGIN_ATTEMPTS=5
LOGIN_WINDOW_SECONDS=900
MAX_SESSIONS=5
INVITATION_ENABLED=true
INVITATION_HOURS=168
EMAIL_VERIFICATION_ENABLED=false
EMAIL_VERIFICATION_MINUTES=60
BOOTSTRAP_ADMIN_EMAIL=<已有账号的邮箱，用于首次授予管理员权限>
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

“退出全部设备”撤销刷新会话；不要把它与成员停用混为同一动作。已启用的成员治理在下一次联网请求检查账号状态和持久会话代次，恢复成员必须重新登录；既有代次保护不因管理开关关闭而失效。离线缓存仍受24小时身份期限约束，不承诺远程擦除。

生产0.1.20已按维护者确认启用双邮箱变更：当前密码、新旧邮箱分别主动验证、本人最终再次确认密码，完成后撤销旧访问／刷新会话与提醒授权，计划和本机队列不删除。令牌仅存摘要，30分钟有效，邮件链接GET不写入、不抄送；失败不覆盖此前有效请求。部署默认值仍为`EMAIL_CHANGE_ENABLED=false`，本项目正式新发布环境经批准设为true；隔离环境真实双邮箱收信及本人确认已通过，没有更改管理员正式邮箱。详见[0.1.20记录](release-0120.md)。不得直接更新数据库邮箱代替验证。生产仍仅限受邀使用；账号注销、公众注册与扩大试用不因本轮自动启用。
