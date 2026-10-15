#!/bin/sh
# Reuse only the exact read-only secret mount already approved on an AI candidate.
# Does not read the key, change host permissions, or require a root shell.
set -eu
donor=${1:?Approved candidate container required}
image=${2:?Verified backend image required}
case "$donor" in plan-ai-[0-9]*-[a-f0-9]*) ;; *) exit 1;; esac
case "$donor" in *[!a-z0-9-]*) exit 1;; esac
case "$image" in plan-platform-api:[0-9]*.[0-9]*.[0-9]*) ;; *) exit 1;; esac
label=$(docker inspect -f '{{index .Config.Labels "plan.ai.candidate"}}' "$donor")
case "$label" in /home/*/plan-platform/ai-candidates/*|/opt/plan-platform/ai-candidates/*) ;; *) echo 'Unapproved secret donor'; exit 1;; esac
mounts=$(docker inspect -f '{{range .Mounts}}{{.Type}}|{{.Source}}|{{.Destination}}|{{.RW}}{{println}}{{end}}' "$donor")
[ "$mounts" = 'bind|/etc/plan-platform-ai/deepseek-api-key|/run/secrets/deepseek-api-key|false' ] || { echo 'Expected exactly the previously approved read-only secret mount'; exit 1; }
[ "$(docker image inspect -f '{{.Config.User}}' "$image")" = plan ] || exit 1
docker run --rm --network none --read-only --cap-drop ALL --security-opt no-new-privileges:true \
  --volumes-from "$donor:ro" --entrypoint sh "$image" -c '
    test "$(id -u)" = 100 && test "$(id -g)" = 101 &&
    test -f /run/secrets/deepseek-api-key && test ! -L /run/secrets/deepseek-api-key &&
    test "$(stat -c %u:%g:%a /run/secrets/deepseek-api-key)" = 100:101:600 &&
    test -r /run/secrets/deepseek-api-key'
echo 'Approved read-only key mount and non-root readability verified; key value not read.'
