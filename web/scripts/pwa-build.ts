import { createHash } from 'node:crypto';
import { readFileSync } from 'node:fs';
import type { Plugin } from 'vite';

export function pwaBuild(): Plugin {
  return {
    name: 'plan-platform-offline-shell', enforce: 'post', apply: 'build',
    generateBundle(_options, bundle) {
      const assets = Object.keys(bundle).filter(name => /\.(js|css)$/.test(name)).sort();
      const shell = ['/', '/index.html', '/manifest.webmanifest', '/icon.svg', ...assets.map(name => `/${name}`)];
      const template = readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8');
      const version = createHash('sha256').update(template).update(JSON.stringify(assets)).digest('hex').slice(0, 16);
      const source = template.replace("'plan-platform-shell-dev'", `'plan-platform-shell-${version}'`)
        .replace(/const SHELL = \[[^;]*\];/, `const SHELL = ${JSON.stringify(shell)};`);
      this.emitFile({ type: 'asset', fileName: 'sw.js', source });
    },
  };
}
