import assert from 'node:assert/strict';
export function clientStaticConfig(base) {
  const markers=[
    '  location = /downloads/manifest.json { add_header Cache-Control "no-store"; try_files $uri =404; }',
    '  location /downloads/ { try_files $uri =404; }',
    '  location ~ ^/client-updates/(windows|android)/latest\\.json$ { add_header Cache-Control "no-store"; try_files $uri =404; }',
    '  location /client-updates/ { try_files $uri =404; }',
  ];
  let result=base.replaceAll('\r\n','\n');
  for(const marker of markers) { assert.equal(result.split(marker).length,2,'Unexpected base Nginx static config; preserve it for review.');result=result.replace(marker,''); }
  const staticLocations=`  # Windows publication only. No alias to private metadata or APKs.
  location = /downloads/manifest.json { root /srv/plan-clients/current/public; add_header Cache-Control "no-store"; try_files $uri =404; }
  location ^~ /downloads/ {
    root /srv/plan-clients/current/public;
    add_header Cache-Control "public, max-age=31536000, immutable";
    try_files $uri =404;
  }
  location = /client-updates/windows/latest.json { root /srv/plan-clients/current/public; add_header Cache-Control "no-store"; try_files $uri =404; }
  location = /client-updates/android/latest.json { return 404; }
  location ^~ /client-updates/ {
    root /srv/plan-clients/current/public;
    add_header Cache-Control "public, max-age=31536000, immutable";
    try_files $uri =404;
  }
`;
  assert.equal(result.split('  location = /index.html').length,2);
  return result.replace('  location = /index.html',staticLocations+'  location = /index.html');
}
