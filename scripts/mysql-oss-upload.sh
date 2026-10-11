#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
backup=${1:?Pass a verified MySQL backup}
gzip -t "$backup"
(cd "$(dirname "$backup")"; sha256sum -c "$(basename "$backup").sha256")
prefix=oss://zj-ecs-server/plan-console-backups/mysql/
# ossutil v2 validates uploads internally; --checksum is OSS-to-OSS only.
ossutil cp "$backup" "$prefix$(basename "$backup")" --mode EcsRamRole --region cn-hangzhou --endpoint https://oss-cn-hangzhou-internal.aliyuncs.com
ossutil cp "$backup.sha256" "$prefix$(basename "$backup").sha256" --mode EcsRamRole --region cn-hangzhou --endpoint https://oss-cn-hangzhou-internal.aliyuncs.com
echo 'MySQL backup and SHA256 sidecar uploaded to private OSS; local files retained.'
