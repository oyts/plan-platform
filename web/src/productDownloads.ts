export interface ProductDownload { version: string; url: string; sha256: string; bytes: number }
export interface ProductDownloads { windows: ProductDownload | null; android: ProductDownload | null }
function download(value: unknown, extension: string, origin: string): ProductDownload | null {
  if (!value || typeof value !== 'object') return null;
  const item = value as Record<string, unknown>;
  if (typeof item.version !== 'string' || !/^\d+\.\d+\.\d+(?:-[a-zA-Z0-9.-]+)?$/.test(item.version)) return null;
  if (typeof item.sha256 !== 'string' || !/^[a-f0-9]{64}$/i.test(item.sha256) || !Number.isSafeInteger(item.bytes) || (item.bytes as number) <= 0) return null;
  if (typeof item.url !== 'string') return null;
  try {
    const url = new URL(item.url, origin);
    // Published installers must stay on this site's HTTPS download directory.
    if (url.origin !== new URL(origin).origin || url.username || url.password || url.search || url.hash || !url.pathname.startsWith('/downloads/') || !url.pathname.toLowerCase().endsWith(extension)) return null;
    if (url.protocol !== 'https:' && !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)) return null;
    return { version: item.version, url: url.href, sha256: item.sha256, bytes: item.bytes as number };
  } catch { return null; }
}
export function parseProductDownloads(value: unknown, origin: string): ProductDownloads {
  if (!value || typeof value !== 'object' || (value as {schemaVersion?:number}).schemaVersion !== 1) throw new Error('下载清单格式不受支持。');
  const data = value as Record<string, unknown>;
  return { windows: download(data.windows, '.exe', origin), android: download(data.android, '.apk', origin) };
}
export function downloadSize(bytes: number): string { return `${(bytes / 1024 / 1024).toFixed(1)} MB`; }
