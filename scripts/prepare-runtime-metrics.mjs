// Local, bounded deployment template only. Does not activate any collector/mail.
import {readFileSync,writeFileSync,mkdirSync,mkdtempSync} from 'node:fs';import {join,resolve} from 'node:path';
import {clientStaticConfig} from './client-static-config.mjs';import {runtimeMetricsConfig} from './runtime-metrics-config.mjs';
const root=process.cwd(),outRoot=resolve('production-artifacts/runtime-metrics');mkdirSync(outRoot,{recursive:true});const out=mkdtempSync(join(outRoot,'candidate-'));
const config=runtimeMetricsConfig(clientStaticConfig(readFileSync('web/nginx.production.conf','utf8')));
writeFileSync(join(out,'nginx.conf'),config,{flag:'wx'});
writeFileSync(join(out,'compose.yml'),`services:
  backend:
    environment:
      METRICS_ENABLED: "true"
      TELEMETRY_ENABLED: "true"
      TELEMETRY_STATUS_FILE: /run/plan-operations/runtime.json
      TELEMETRY_MAIL_ALERTS_ENABLED: "false"
  web:
    volumes:
      - /home/oyts/plan-platform/telemetry/logs:/var/log/plan-metrics
`,{flag:'wx'});
writeFileSync(join(out,'collect.sh'),`#!/bin/sh
set -eu
umask 027
root=/home/oyts/plan-platform
exec 9>"$root/telemetry/collect.lock"
flock -n 9 || exit 0
python3 "$root/current/scripts/runtime-telemetry.py" --log "$root/telemetry/logs/plan-metrics.log" --state "$root/telemetry/runtime-cursor.json" --output "$root/operations/runtime.json"
`,{flag:'wx'});
writeFileSync(join(out,'logrotate.conf'),`/home/oyts/plan-platform/telemetry/logs/plan-metrics.log {
  size 4M
  rotate 2
  missingok
  notifempty
  create 0640 oyts oyts
  su oyts oyts
  sharedscripts
  postrotate
    docker kill --signal USR1 plan-platform-web-1 >/dev/null
  endscript
}
`,{flag:'wx'});
console.log('Runtime metrics templates only: '+out);console.log('No production activation, cron, mail, private log read or deletion. Review overlay retention and logrotate before enabling.');
