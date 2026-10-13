import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
export const ORIGIN='https://plan.zijun701.cn';
export const CLOSED_LOCATION='  location = /client-updates/android/latest.json { return 404; }';
export function bridgePaths(run,version) {
  assert.match(run,/^bridge-[a-zA-Z0-9_-]{1,48}$/);
  assert.match(version,/^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/);
  return {file:`PlanCabin-${version}-release.apk`,uri:`/client-updates/android/${run}/PlanCabin-${version}-release.apk`,directory:`/srv/plan-clients/legacy-android/${run}`};
}
export function bridgeManifest(manifest,run,expires,now=Math.floor(Date.now()/1000)) {
  assert.ok(Number.isSafeInteger(expires)&&expires>now&&expires<=now+72*3600,'Migration expires within 72 hours');
  assert.equal(manifest.packageName,'cn.zijun701.plan.app');
  assert.ok(Number.isSafeInteger(manifest.versionCode)&&manifest.versionCode>8);
  assert.match(manifest.sha256,/^[a-f0-9]{64}$/);
  assert.ok(Number.isInteger(manifest.size)&&manifest.size>0&&manifest.size<=256*1024*1024);
  const paths=bridgePaths(run,manifest.version);
  return {...manifest,minimumSupportedVersionCode:1,url:ORIGIN+paths.uri,migrationExpiresAt:new Date(expires*1000).toISOString()};
}
export function bridgeConfig(base,run,manifest,expires) {
  assert.equal(base.split(CLOSED_LOCATION).length,2,'Expected one closed legacy Android location');
  assert.ok(Number.isSafeInteger(expires)&&expires>0&&expires<=2147483647);
  const paths=bridgePaths(run,manifest.version);
  // The expiration is a literal, never an argument chosen by an anonymous
  // caller. secure_link checks server time on BOTH endpoints, independent of
  // cron, agent availability, query strings and any cached client manifest.
  const scope=`${expires}:plan-legacy-android:${run}`;
  const digest=createHash('md5').update(scope).digest('base64url');
  const guard=`    secure_link "${digest},${expires}";\n    secure_link_md5 "$secure_link_expires:plan-legacy-android:${run}";\n    if ($secure_link = "") { return 404; }\n    if ($secure_link = "0") { return 404; }\n    add_header Cache-Control "no-store" always;\n    disable_symlinks on;\n    limit_except GET { deny all; }\n`;
  const locations=`  # TEMPORARY LEGACY ANDROID MIGRATION ${run}; expires ${expires}\n  location = /client-updates/android/latest.json {\n${guard}    default_type application/json;\n    alias ${paths.directory}/latest.json;\n  }\n  location = ${paths.uri} {\n${guard}    default_type application/vnd.android.package-archive;\n    max_ranges 0;\n    alias ${paths.directory}/${paths.file};\n  }\n  location ^~ /client-updates/android/ { return 404; }`;
  return base.replace(CLOSED_LOCATION,locations);
}
