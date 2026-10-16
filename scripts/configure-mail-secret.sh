#!/bin/sh
# Owner-interactive only. Creates a NEW restricted QQ SMTP candidate, never activates it.
set -eu
umask 077
[ "$(id -u)" = 0 ] || { echo 'Run with sudo in the server terminal.'; exit 1; }
[ -t 0 ] || { echo 'An interactive terminal is required; never pass authorization codes as arguments.'; exit 1; }
directory=/etc/plan-platform-mail
[ ! -e "$directory" ] || { echo 'Existing mail configuration retained. Do not overwrite credentials.'; exit 1; }
printf 'QQ sender email (your own address): '
IFS= read -r sender
printf '%s' "$sender" | grep -Eq '^[0-9]{5,12}@qq\.com$' || { echo 'Expected your own numeric QQ email address.'; exit 1; }
printf 'SMTP authorization code (hidden, NOT the QQ login password): '
old=$(stty -g)
trap 'stty "$old"' EXIT HUP INT TERM
stty -echo
IFS= read -r code
stty "$old"
trap - EXIT HUP INT TERM
printf '\n'
printf '%s' "$code" | grep -Eq '^[A-Za-z0-9]{12,128}$' || { code=; echo 'Invalid code format. No files saved.'; exit 1; }
install -d -m 700 "$directory"
# Spring configtree maps filenames to property names. Values are never printed.
(set -C; printf '%s' "$code" > "$directory/spring.mail.password")
code=
chown 100:101 "$directory/spring.mail.password"
chmod 600 "$directory/spring.mail.password"
printf '%s' "$sender" > "$directory/sender"
chmod 600 "$directory/sender"
echo 'Restricted SMTP candidate saved. No service restarted, no email sent, password recovery remains disabled.'
echo 'Next: prepare the approved read-only mail overlay, send an administrator test, then confirm receipt before enabling recovery.'
