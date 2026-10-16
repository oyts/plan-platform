#!/bin/sh
# Root prepares only an inactive candidate; no Docker/restart/SMTP request.
set -eu
umask 077
[ "$(id -u)" = 0 ] || exit 1
directory=/etc/plan-platform-mail
test -f "$directory/spring.mail.password" && test ! -L "$directory/spring.mail.password"
test "$(stat -c %u:%g:%a "$directory/spring.mail.password")" = 100:101:600
sender=$(cat "$directory/sender")
printf '%s' "$sender" | grep -Eq '^[0-9]{5,12}@qq\.com$' || exit 1
target=/home/oyts/plan-platform/production-mail.candidate.yml
[ ! -e "$target" ] || { echo 'Existing candidate retained.'; exit 1; }
(set -C; cat > "$target" <<EOF
services:
  backend:
    environment:
      SPRING_CONFIG_IMPORT: optional:configtree:/run/secrets/mail/
      SPRING_MAIL_HOST: smtp.qq.com
      SPRING_MAIL_PORT: "465"
      SPRING_MAIL_USERNAME: "$sender"
      MAIL_FROM: "$sender"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH: "true"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE: "true"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_CHECKSERVERIDENTITY: "true"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT: "5000"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT: "5000"
      SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT: "5000"
      PASSWORD_RESET_ENABLED: "false"
      EMAIL_VERIFICATION_ENABLED: "false"
    volumes:
      - /etc/plan-platform-mail:/run/secrets/mail:ro
EOF
)
# The backend needs directory traversal inside the mount, not world readability.
chown 100:101 "$directory"
chown 100:101 "$directory/sender"
chmod 700 "$directory"
chown oyts:oyts "$target"
chmod 600 "$target"
echo 'Inactive mail overlay prepared. Credentials not printed. Activation and actual inbox verification still required.'
