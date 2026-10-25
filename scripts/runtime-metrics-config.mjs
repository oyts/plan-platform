import assert from 'node:assert/strict';
export function runtimeMetricsConfig(base){
 assert.equal(base.split('server {').length,2,'Expected one known web server');assert.ok(!base.includes('plan_metric_route'),'Metrics configuration already present');
 const maps=`# Dedicated metrics never writes the source variables to its log.
map "$request_method:$request_uri" $plan_metric_route {
 default other;
 ~^GET:/(welcome)?(\\?|$) public;
 ~^[A-Z]+:/api/auth/(login|refresh)(\\?|$) auth;
 ~^[A-Z]+:/api/ api;
 ~^[A-Z]+:/(\\.env|\\.git|wp-admin|phpmyadmin)(/|\\?|$) scan;
}
map $http_user_agent $plan_metric_bot { default 0; ~*(bot|crawler|spider) 1; }
map $request_uri $plan_metric_loggable { default 1; ~^/actuator/ 0; ~^/api/health 0; ~^/api/public/analytics/ 0; }
log_format plan_private_metrics '$msec $plan_metric_route $status $request_time $plan_metric_bot';
`;
 return maps+base.replace('  listen 80;','  listen 80;\n  access_log /var/log/plan-metrics/plan-metrics.log plan_private_metrics if=$plan_metric_loggable;');
}
